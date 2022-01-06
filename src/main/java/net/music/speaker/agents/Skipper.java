package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.music.speaker.Application;
import net.music.speaker.JsonSerializable;
import net.music.speaker.models.SkipVoteResult;
import net.music.speaker.WebServer;

import java.time.Duration;

public class Skipper {
    public static final ServiceKey<Command> skipperServiceKey =
            ServiceKey.create(Command.class, "skipper");

    interface Command extends JsonSerializable {}
    public record VoteButtonPressed() implements Command {}
    public record VotingStarted(ActorRef<Moderator.Command> replyTo, Duration votingDuration) implements Command {}
    private record ListingResponse(Receptionist.Listing listing) implements Command {}
    private record VotingTimeout() implements Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(
            context -> {
                context
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.register(skipperServiceKey, context.getSelf()));

                return Behaviors.withTimers(timers -> new AwaitStartVoteBehaviour(context, timers));
            });
    }

    private static class AwaitStartVoteBehaviour extends AbstractBehavior<Command> {
        private final ActorRef<Receptionist.Listing> listingResponseAdapter;
        private final TimerScheduler<Command> timers;

        public AwaitStartVoteBehaviour(ActorContext<Command> context, TimerScheduler<Command> timers) {
            super(context);
            this.listingResponseAdapter = context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);
            this.timers = timers;
            getContext().getLog().info("Changed behaviour to " + getClass().getSimpleName());
        }

        private Behavior<Command> onVoteButtonPressed(VoteButtonPressed msg) {
            getContext()
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.find(Moderator.moderatorServiceKey, listingResponseAdapter));
            return new RequestStartVoteModeratorListingBehaviour(getContext(), timers);
        }
        
        private Behavior<Command> onStartVote(VotingStarted msg) {
            Application.webServer.sendBroadcast(new WebServer.VoteStarted());
            return new VoteInProgressBehaviour(getContext(), timers, msg.replyTo, msg.votingDuration, false);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(VoteButtonPressed.class, this::onVoteButtonPressed)
                    .onMessage(VotingStarted.class, this::onStartVote)
                    .build();
        }
    }

    private static class VoteInProgressBehaviour extends AbstractBehavior<Command> {
        private final TimerScheduler<Command> timers;
        private final ActorRef<Moderator.Command> replyTo;
        private final Duration votingDuration;
        private final boolean alreadyVoted;

        public VoteInProgressBehaviour(ActorContext<Command> context, TimerScheduler<Command> timers, ActorRef<Moderator.Command> replyTo, Duration votingDuration, boolean alreadyVoted) {
            super(context);
            this.timers = timers;
            this.replyTo = replyTo;
            this.votingDuration = votingDuration;
            this.alreadyVoted = alreadyVoted;
            if (!alreadyVoted) {
                timers.startSingleTimer(new VotingTimeout(), votingDuration);
            }
        }

        private Behavior<Command> onVoteButtonPressed(VoteButtonPressed msg) {
            if (alreadyVoted) {
                getContext().getLog().warn("Already voted");
                return Behaviors.same();
            }
            replyTo.tell(new Moderator.VoteResponse(new SkipVoteResult(true), getContext().getSelf()));
            return new VoteInProgressBehaviour(getContext(), timers, replyTo, votingDuration, true);
        }

        private Behavior<Command> onVoteTimeout(VotingTimeout msg) {
            Application.webServer.sendBroadcast(new WebServer.VoteFinished());
            return new AwaitStartVoteBehaviour(getContext(), timers);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(VoteButtonPressed.class, this::onVoteButtonPressed)
                    .onMessage(VotingTimeout.class, this::onVoteTimeout)
                    .build();
        }
    }

    private static class RequestStartVoteModeratorListingBehaviour extends AbstractBehavior<Command> {
        private final TimerScheduler<Command> timers;

        public RequestStartVoteModeratorListingBehaviour(ActorContext<Command> context, TimerScheduler<Command> timers) {
            super(context);
            this.timers = timers;
            getContext().getLog().info("Changed behaviour to " + getClass().getSimpleName());
        }

        private Behavior<Command> onListing(ListingResponse msg) {
            msg.listing.getServiceInstances(Moderator.moderatorServiceKey)
                    .forEach(service -> service.tell(new Moderator.StartVoteRequest(getContext().getSelf())));
            return new AwaitStartVoteBehaviour(getContext(), timers);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(ListingResponse.class, this::onListing)
                    .build();
        }
    }

}
