package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

public class Skipper {
    public static final ServiceKey<Skipper.Command> skipperServiceKey =
            ServiceKey.create(Skipper.Command.class, "skipper");

    interface Command {}
    public static final class VotingStarted implements Skipper.Command {}

    static final class ListingResponse implements Skipper.Command {
        final Receptionist.Listing listing;

        ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    public static Behavior<Skipper.Command> create() {
        return Behaviors.setup(
            context -> {
                context
                        .getSystem()
                        .receptionist()
                        .tell(Receptionist.register(skipperServiceKey, context.getSelf()));

                return new AwaitStartVoteBehaviour(context);
            });
    }

    static class AwaitStartVoteBehaviour extends AbstractBehavior<Skipper.Command> {
        private final ActorRef<Receptionist.Listing> listingResponseAdapter;

        public AwaitStartVoteBehaviour(ActorContext<Skipper.Command> context) {
            super(context);
            this.listingResponseAdapter = context.messageAdapter(Receptionist.Listing.class, Skipper.ListingResponse::new);
            getContext().getLog().info("Changed behaviour to " + getClass().getSimpleName());
        }

        private Behavior<Skipper.Command> onStartVote(VotingStarted msg) {
            getContext()
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.find(Moderator.moderatorServiceKey, listingResponseAdapter));
            return new ModeratorListingBehaviour(getContext());
        }

        @Override
        public Receive<Skipper.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(VotingStarted.class, this::onStartVote)
                    .build();
        }
    }

    static class ModeratorListingBehaviour extends AbstractBehavior<Skipper.Command> {
        public ModeratorListingBehaviour(ActorContext<Skipper.Command> context) {
            super(context);
            getContext().getLog().info("Changed behaviour to " + getClass().getSimpleName());
        }

        private Behavior<Skipper.Command> onListing(Skipper.ListingResponse msg) {
            msg.listing.getServiceInstances(Moderator.moderatorServiceKey)
                    .forEach(service -> service.tell(new Moderator.VoteRequest()));
            return new AwaitStartVoteBehaviour(getContext());
        }

        @Override
        public Receive<Skipper.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Skipper.ListingResponse.class, this::onListing)
                    .build();
        }
    }

}
