package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.music.speaker.JsonSerializable;
import net.music.speaker.models.SkipVoteResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Moderator {
    public static final ServiceKey<Command> moderatorServiceKey =
            ServiceKey.create(Command.class, "moderator");

    private static final Duration VOTING_DURATION = Duration.ofSeconds(10);

    interface Command extends JsonSerializable {}
    public record StartVoteRequest(ActorRef<Skipper.Command> voter) implements Command {}
    public record VoteResponse(SkipVoteResult response, ActorRef<Skipper.Command> voter) implements Command {}
    private record VoteTimeout() implements Command {}
    private record SkipperListingResponse(Receptionist.Listing listing) implements Command {}
    private record SpeakerListingResponse(Receptionist.Listing listing) implements Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(
            context -> {
                context
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.register(moderatorServiceKey, context.getSelf()));

                return Behaviors.withTimers(timers -> new AwaitRequestStartVoteBehaviour(context, timers));
            });
    }

    private static class AwaitRequestStartVoteBehaviour extends AbstractBehavior<Command> {
        private final TimerScheduler<Command> timers;

        public AwaitRequestStartVoteBehaviour(ActorContext<Command> context, TimerScheduler<Command> timers) {
            super(context);
            this.timers = timers;
        }

        private Behavior<Command> onStartVoteRequest(StartVoteRequest msg) {
            getContext().getLog().info("Start vote request received");

            getContext()
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.find(
                            Skipper.skipperServiceKey,
                            getContext().messageAdapter(Receptionist.Listing.class, SkipperListingResponse::new)
                    ));

            return new WaitingForStartVoteListingBehaviour(getContext(), timers, msg.voter);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(StartVoteRequest.class, this::onStartVoteRequest)
                    .build();
        }
    }

    private static class WaitingForStartVoteListingBehaviour extends AbstractBehavior<Command> {
        private final TimerScheduler<Command> timers;
        private final ActorRef<Skipper.Command> startVoter;

        public WaitingForStartVoteListingBehaviour(ActorContext<Command> context, TimerScheduler<Command> timers, ActorRef<Skipper.Command> startVoter) {
            super(context);
            this.timers = timers;
            this.startVoter = startVoter;
        }

        private Behavior<Command> onListing(SkipperListingResponse msg) {
            Set<ActorRef<Skipper.Command>> skippers = msg.listing.getServiceInstances(Skipper.skipperServiceKey);

            Skipper.VotingStarted votingStarted = new Skipper.VotingStarted(getContext().getSelf(), VOTING_DURATION);
            skippers.forEach(skipperService -> skipperService.tell(votingStarted));

            return new AwaitVoteBehaviour(getContext(), timers, VOTING_DURATION, startVoter, skippers);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(SkipperListingResponse.class, this::onListing)
                    .build();
        }
    }

    private static class AwaitVoteBehaviour extends AbstractBehavior<Command> {
        private final TimerScheduler<Command> timers;
        private final List<VoteResponse> votes = new ArrayList<>();
        private final Set<ActorRef<Skipper.Command>> participants;

        private static final Object TIMER_KEY = new Object();

        public AwaitVoteBehaviour(ActorContext<Command> context, TimerScheduler<Command> timers, Duration votingDuration, ActorRef<Skipper.Command> startVoter, Set<ActorRef<Skipper.Command>> participants) {
            super(context);
            this.timers = timers;
            this.participants = participants;
            votes.add(new VoteResponse(new SkipVoteResult(true), startVoter));
            timers.startSingleTimer(TIMER_KEY, new VoteTimeout(), VOTING_DURATION);
        }

        private Behavior<Command> onVote(VoteResponse msg) {
            if (votes.stream().anyMatch(vote -> vote.voter.equals(msg.voter))) {
                getContext().getLog().info("Received duplicated vote");
                return this;
            }

            votes.add(msg);
            getContext().getLog().info("Received vote");

            return this;
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(VoteResponse.class, this::onVote)
                    .onMessage(VoteTimeout.class, this::onVoteTimeout)
                    .build();
        }

        private Behavior<Command> onVoteTimeout(VoteTimeout m) {
            long wantSkipCount = votes.stream().filter(voteRequest -> voteRequest.response.wantSkip).count();
            double percentageResult = (double)wantSkipCount / participants.size();

            if (percentageResult > 0.5) {
                getContext()
                        .getSystem()
                        .receptionist()
                        .tell(Receptionist.find(
                                Speaker.speakerServiceKey,
                                getContext().messageAdapter(Receptionist.Listing.class, SpeakerListingResponse::new)
                        ));

                return new AwaitSpeakerListing(getContext(), timers);
            }

            return new AwaitRequestStartVoteBehaviour(getContext(), timers);
        }
    }

    private static class AwaitSpeakerListing extends AbstractBehavior<Command> {
        private final TimerScheduler<Command> timers;

        public AwaitSpeakerListing(ActorContext<Command> context, TimerScheduler<Command> timers) {
            super(context);
            this.timers = timers;
        }

        private Behavior<Command> onListingResponse(SpeakerListingResponse msg) {
            msg.listing.getServiceInstances(Speaker.speakerServiceKey)
                    .forEach(speaker -> speaker.tell(new Speaker.SkipSongRequest()));
            return new AwaitRequestStartVoteBehaviour(getContext(), timers);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(SpeakerListingResponse.class, this::onListingResponse)
                    .build();
        }
    }
}
