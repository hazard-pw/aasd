package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

import java.util.ArrayList;
import java.util.List;

public class Moderator {
    public static final ServiceKey<Moderator.Command> moderatorServiceKey =
            ServiceKey.create(Moderator.Command.class, "moderator");

    interface Command {}
    public static final class StartVoteRequest implements Moderator.Command {}
    public static final class VoteRequest implements Moderator.Command {}

    static final class ListingResponse implements Moderator.Command {
        final Receptionist.Listing listing;

        ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    public static Behavior<Moderator.Command> create() {
        return Behaviors.setup(
            context -> {
                context
                        .getSystem()
                        .receptionist()
                        .tell(Receptionist.register(moderatorServiceKey, context.getSelf()));

                return new Moderator.AwaitRequestStartVoteBehaviour(context);
            });
    }

    static class AwaitRequestStartVoteBehaviour extends AbstractBehavior<Moderator.Command> {
        private final ActorRef<Receptionist.Listing> listingResponseAdapter;

        public AwaitRequestStartVoteBehaviour(ActorContext<Moderator.Command> context) {
            super(context);
            this.listingResponseAdapter = context.messageAdapter(Receptionist.Listing.class, Moderator.ListingResponse::new);
            getContext().getLog().info("Changed behaviour to " + getClass().getSimpleName());
        }

        private Behavior<Moderator.Command> onVoteRequest(Moderator.StartVoteRequest msg) {
            getContext()
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.find(Skipper.skipperServiceKey, listingResponseAdapter));

            return new WaitingForStartVoteListingBehaviour(getContext());
        }

        @Override
        public Receive<Moderator.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Moderator.StartVoteRequest.class, this::onVoteRequest)
                    .build();
        }
    }

    static class WaitingForStartVoteListingBehaviour extends AbstractBehavior<Moderator.Command> {
        public WaitingForStartVoteListingBehaviour(ActorContext<Moderator.Command> context) {
            super(context);
            getContext().getLog().info("Changed behaviour to " + getClass().getSimpleName());
        }

        private Behavior<Moderator.Command> onListing(Moderator.ListingResponse msg) {
            msg.listing.getServiceInstances(Skipper.skipperServiceKey)
                    .forEach(skipperService -> skipperService.tell(new Skipper.VotingStarted()));
            return new AwaitVoteBehaviour(getContext());
        }

        @Override
        public Receive<Moderator.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Moderator.ListingResponse.class, this::onListing)
                    .build();
        }
    }

    static class AwaitVoteBehaviour extends AbstractBehavior<Moderator.Command> {
        private final List<Moderator.VoteRequest> votes = new ArrayList<>();

        public AwaitVoteBehaviour(ActorContext<Moderator.Command> context) {
            super(context);
            getContext().getLog().info("Changed behaviour to " + getClass().getSimpleName());
        }

        private Behavior<Moderator.Command> onVote(Moderator.VoteRequest msg) {
            votes.add(msg);
            getContext().getLog().info("Received vote");

            if (votes.size() == 1) {
                // todo send skip request to speaker
                return new AwaitRequestStartVoteBehaviour(getContext());
            }

            return this;
        }

        @Override
        public Receive<Moderator.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Moderator.VoteRequest.class, this::onVote)
                    .build();
        }
    }
}
