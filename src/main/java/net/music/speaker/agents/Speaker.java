package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.util.Timeout;
import com.google.gson.Gson;
import org.slf4j.Logger;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Speaker {
    interface Command { }
    private record FindSurveyorResponse(Receptionist.Listing surveyors) implements Command {}
    private record RequestSongSuccess(Surveyor.NextSongResponse nextSong) implements Command {}
    private record RequestSongRetry() implements Command {}
    private record SongStartSuccess() implements Command {}
    private record CheckPlayingTimer() implements Command {}
    private record SongFinished() implements Command {}
    public record SkipSongRequest() implements Command {}

    public static Behavior<Command> create(SpotifyApi spotifyApi) {
        return Behaviors.setup(context -> {
            context.getSystem().receptionist().tell(
                    Receptionist.subscribe(
                            Surveyor.surveyorServiceKey,
                            context.messageAdapter(Receptionist.Listing.class, Speaker.FindSurveyorResponse::new)
                    )
            );

            // If any music was already playing, stop it first
            final Logger logger = context.getLog();
            findSpotifyDevice(spotifyApi)
                .thenCompose(deviceId ->
                    spotifyApi.pauseUsersPlayback()
                        .device_id(deviceId)
                        .build()
                        .executeAsync())
                .handle((response, throwable) -> {
                    if (throwable == null) {
                        logger.info("Playback stopped");
                    } else {
                        logger.warn("Stopping playback failed", throwable);
                    }
                    return null;
                });

            return Behaviors.withTimers(timers -> new WaitForNextSongBehavior(context, timers, spotifyApi, null));
        });
    }

    private static CompletableFuture<String> findSpotifyDevice(SpotifyApi spotifyApi) {
        return spotifyApi.getUsersAvailableDevices()
            .build()
            .executeAsync()
            .thenApply(devices -> {
                for (Device device : devices) {
                    if ("Computer".equals(device.getType())) {
                        return device.getId();
                    }
                }
                throw new UnsupportedOperationException("Unable to find the target Spotify device");
            });
    }

    private abstract static class BehaviorWithSurveyor<T> extends AbstractBehavior<T> {
        protected final ActorRef<Surveyor.Command> surveyor;

        public BehaviorWithSurveyor(ActorContext<T> context, ActorRef<Surveyor.Command> surveyor) {
            super(context);
            this.surveyor = surveyor;
        }

        protected Behavior<Command> onSurveyorList(FindSurveyorResponse msg) {
            Set<ActorRef<Surveyor.Command>> surveyors = msg.surveyors.getServiceInstances(Surveyor.surveyorServiceKey);
            getContext().getLog().info("Surveyor list update");
            if (surveyors.size() == 0) {
                getContext().getLog().warn("No surveyors, waiting");
                return onChangeSurveyor(null);
            }
            if (surveyors.size() > 1) {
                getContext().getLog().warn("There is more than one surveyor?! This shouldn't have happened, using the first one");
            }
            ActorRef<Surveyor.Command> surveyor = surveyors.iterator().next();
            getContext().getLog().info("Changing Surveyor to " + surveyor);
            return onChangeSurveyor(surveyor);
        }

        protected abstract Behavior<Command> onChangeSurveyor(ActorRef<Surveyor.Command> surveyor);
    }

    public static class WaitForNextSongBehavior extends BehaviorWithSurveyor<Speaker.Command> {
        private final TimerScheduler<Command> timers;
        private final SpotifyApi spotifyApi;

        public WaitForNextSongBehavior(ActorContext<Command> context, TimerScheduler<Command> timers, SpotifyApi spotifyApi, ActorRef<Surveyor.Command> surveyor) {
            super(context, surveyor);
            this.timers = timers;
            this.spotifyApi = spotifyApi;
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(FindSurveyorResponse.class, this::onSurveyorList)
                    .onMessage(RequestSongRetry.class, this::onRequestSongRetry)
                    .onMessage(RequestSongSuccess.class, this::onRequestSongSuccess)
                    .onMessage(SongStartSuccess.class, this::onStartSongSuccess)
                    .build();
        }

        @Override
        protected Behavior<Command> onChangeSurveyor(ActorRef<Surveyor.Command> surveyor) {
            if (this.surveyor == null && surveyor != null) {
                requestNextSong(surveyor);
            }
            return new WaitForNextSongBehavior(getContext(), timers, spotifyApi, surveyor);
        }

        private Behavior<Command> onRequestSongRetry(RequestSongRetry msg) {
            if (surveyor != null) {
                requestNextSong(surveyor);
            }
            return Behaviors.same();
        }

        private Behavior<Command> onRequestSongSuccess(RequestSongSuccess msg) {
            final Logger logger = getContext().getLog();
            final ActorRef<Speaker.Command> self = getContext().getSelf();
            findSpotifyDevice(spotifyApi)
                .thenCompose(deviceId ->
                    spotifyApi.startResumeUsersPlayback()
                        .device_id(deviceId)
                        .uris(new Gson().toJsonTree(new String[] { msg.nextSong().songURI() }).getAsJsonArray())
                        .build()
                        .executeAsync())
                .handle((response, throwable) -> {
                    if (throwable == null) {
                        self.tell(new SongStartSuccess());
                    } else {
                        logger.warn("Starting playback failed", throwable);
                        self.tell(new RequestSongRetry());
                    }
                    return null;
                });
            return Behaviors.same();
        }

        private Behavior<Command> onStartSongSuccess(SongStartSuccess msg) {
            return new WaitForSongEndBehavior(getContext(), timers, spotifyApi, surveyor);
        }

        public WaitForNextSongBehavior requestNextSong(ActorRef<Surveyor.Command> surveyor) {
            if (surveyor == null)
                return this;

            getContext().ask(
                Surveyor.NextSongResponse.class,
                surveyor,
                Duration.ofSeconds(3),
                Surveyor.NextSongRequest::new,
                (response, throwable) -> {
                    if (throwable == null && response != null) {
                        return new RequestSongSuccess(response);
                    } else {
                        getContext().getLog().warn("Request to Surveyor failed, retrying", throwable);
                        return new RequestSongRetry();
                    }
                }
            );
            return this;
        }
    }

    public static class WaitForSongEndBehavior extends BehaviorWithSurveyor<Speaker.Command> {
        private final TimerScheduler<Command> timers;
        private final SpotifyApi spotifyApi;

        private static final Object TIMER_KEY = new Object();

        public WaitForSongEndBehavior(ActorContext<Command> context, TimerScheduler<Command> timers, SpotifyApi spotifyApi, ActorRef<Surveyor.Command> surveyor) {
            super(context, surveyor);
            this.timers = timers;
            this.spotifyApi = spotifyApi;
            timers.startTimerAtFixedRate(TIMER_KEY, new CheckPlayingTimer(), Duration.ofSeconds(1));
        }

        @Override
        protected Behavior<Command> onChangeSurveyor(ActorRef<Surveyor.Command> surveyor) {
            return new WaitForNextSongBehavior(getContext(), timers, spotifyApi, surveyor);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(FindSurveyorResponse.class, this::onSurveyorList)
                    .onMessage(SkipSongRequest.class, this::onSkipSongRequest)
                    .onMessage(CheckPlayingTimer.class, this::onCheckPlayingTimer)
                    .onMessage(SongFinished.class, this::onSongFinished)
                    .build();
        }

        private Behavior<Command> onCheckPlayingTimer(CheckPlayingTimer msg) {
            final Logger logger = getContext().getLog();
            final ActorRef<Speaker.Command> self = getContext().getSelf();
            spotifyApi.getUsersCurrentlyPlayingTrack()
                    .build()
                    .executeAsync()
                    .handle((response, throwable) -> {
                        if (throwable == null && response != null) {
                            if (!response.getIs_playing()) {
                                logger.info("Song playback finished!");
                                self.tell(new SongFinished());
                            }
                        } else {
                            logger.warn("Querying playback state failed", throwable);
                        }
                        return null;
                    });
            return Behaviors.same();
        }

        private Behavior<Command> onSongFinished(SongFinished msg) {
            timers.cancel(TIMER_KEY);
            return new WaitForNextSongBehavior(getContext(), timers, spotifyApi, surveyor).requestNextSong(surveyor);
        }

        private Behavior<Command> onSkipSongRequest(SkipSongRequest skipSongRequest) {
            timers.cancel(TIMER_KEY);
            return new WaitForNextSongBehavior(getContext(), timers, spotifyApi, surveyor).requestNextSong(surveyor);
        }
    }
}
