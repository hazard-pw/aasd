package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.music.speaker.JsonSerializable;
import net.music.speaker.models.SurveyResult;
import org.slf4j.Logger;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Surveyor {
    public static final ServiceKey<Command> surveyorServiceKey =
            ServiceKey.create(Command.class, "surveyor");

    interface Command extends JsonSerializable {}
    public record ReceiveSurveyResult(SurveyResult result) implements Command {}
    public record NextSongRequest(ActorRef<Surveyor.NextSongResponse> replyTo) implements Command {}
    public record NextSongResponse(String songURI) {}

    public static Behavior<Command> create(SpotifyApi spotifyApi) {
        return Behaviors.setup(
                context -> {
                    context
                            .getSystem()
                            .receptionist()
                            .tell(Receptionist.register(surveyorServiceKey, context.getSelf()));

                    return new SurveyorBehavior(context, spotifyApi, Collections.emptyList());
                });
    }

    private static class SurveyorBehavior extends AbstractBehavior<Command> {
        private final SpotifyApi spotifyApi;
        private final List<SurveyResult> results;

        public SurveyorBehavior(ActorContext<Command> context, SpotifyApi spotifyApi, List<SurveyResult> results) {
            super(context);
            this.spotifyApi = spotifyApi;
            this.results = Collections.unmodifiableList(results); // Remember that the state must be immutable!
        }

        private Behavior<Command> onSurveyResult(ReceiveSurveyResult msg) {
            getContext().getLog().info("Received survey result " + msg.result.toString());
            return new SurveyorBehavior(
                getContext(),
                spotifyApi,
                Stream.concat(results.stream(), Stream.of(msg.result)).toList());
        }

        private SurveyResult calculateSongTarget() {
            // Calculate the average song parameters for use as a recommendation target
            SurveyResult target = new SurveyResult();
            for (SurveyResult result : results) {
                target.danceability += result.danceability;
                target.valence += result.valence;
                target.energy += result.energy;
                target.tempo += result.tempo;
                target.loudness += result.loudness;
                target.speechiness += result.speechiness;
                target.instrumentalness += result.instrumentalness;
                target.liveness += result.liveness;
                target.acousticness += result.acousticness;
            }
            target.danceability /= results.size();
            target.valence /= results.size();
            target.energy /= results.size();
            target.tempo /= results.size();
            target.loudness /= results.size();
            target.speechiness /= results.size();
            target.instrumentalness /= results.size();
            target.liveness /= results.size();
            target.acousticness /= results.size();

            target.artists = results.stream().flatMap(x -> Arrays.stream(x.artists)).distinct().collect(toShuffledStream()).limit(5).toArray(String[]::new);
            target.genres = results.stream().flatMap(x -> Arrays.stream(x.genres)).distinct().collect(toShuffledStream()).limit(5).toArray(String[]::new);
            target.tracks = results.stream().flatMap(x -> Arrays.stream(x.tracks)).distinct().collect(toShuffledStream()).limit(5).toArray(String[]::new);

            return target;
        }

        private static <T> Collector<T, ?, Stream<T>> toShuffledStream() {
            return Collectors.collectingAndThen(Collectors.toList(), collected -> {
                Collections.shuffle(collected);
                return collected.stream();
            });
        }

        private Behavior<Command> onNextSongRequest(NextSongRequest msg) {
            final Logger logger = getContext().getLog();
            if (results.size() == 0) {
                logger.error("Not enough results for recommendations, ignoring request");
                return Behaviors.same();
            }
            SurveyResult songTarget = calculateSongTarget();
            logger.info("Song target: " + songTarget);
            spotifyApi.getRecommendations()
                .seed_artists(String.join(",", songTarget.artists))
                .seed_genres(String.join(",", songTarget.genres))
                .seed_tracks(String.join(",", songTarget.tracks))
                .target_danceability(songTarget.danceability)
                .target_valence(songTarget.valence)
                .target_energy(songTarget.energy)
                .target_tempo(songTarget.tempo)
                .target_loudness(songTarget.loudness)
                .target_speechiness(songTarget.speechiness)
                .target_instrumentalness(songTarget.instrumentalness)
                .target_liveness(songTarget.instrumentalness)
                .target_acousticness(songTarget.acousticness)
                .build()
                .executeAsync()
                .handle((recommendations, throwable) -> {
                    if (throwable == null && recommendations != null) {
                        TrackSimplified[] tracks = recommendations.getTracks();
                        if (tracks.length > 0) {
                            TrackSimplified track = tracks[new Random().nextInt(tracks.length)];
                            logger.info("Selected song: " + track.toString());
                            msg.replyTo.tell(new NextSongResponse(track.getUri()));
                        } else {
                            logger.error("No recommendations returned from the Spotify API?!");
                        }
                    } else {
                        logger.error("Call to Spotify for recommendations failed", throwable);
                    }
                    return null;
                });
            return Behaviors.same();
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(ReceiveSurveyResult.class, this::onSurveyResult)
                    .onMessage(NextSongRequest.class, this::onNextSongRequest)
                    .build();
        }
    }
}
