package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import net.music.speaker.JsonSerializable;
import net.music.speaker.models.SurveyResult;
import net.music.speaker.ui.SurveyUI;

public class Responder {
    interface Command extends JsonSerializable {}
    public record OpenSurvey() implements Command {}
    public record UIFinished(SurveyResult results) implements Command {}
    private record ListingResponse(Receptionist.Listing listing) implements Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(WaitingForOpenSurveyBehaviour::new);
    }

    static class WaitingForOpenSurveyBehaviour extends AbstractBehavior<Responder.Command> {
        public WaitingForOpenSurveyBehaviour(ActorContext<Responder.Command> context) {
            super(context);
        }

        private Behavior<Responder.Command> onOpenSurvey(Responder.OpenSurvey msg) {
            getContext().getLog().info("Survey Open!");

            new SurveyUI((SurveyResult results) -> {
                getContext().getSelf().tell(new Responder.UIFinished(results));
            }).start();

            return new WaitingForUIFinishedBehaviour(getContext());
        }

        @Override
        public Receive<Responder.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Responder.OpenSurvey.class, this::onOpenSurvey)
                    .build();
        }
    }

    static class WaitingForUIFinishedBehaviour extends AbstractBehavior<Responder.Command> {
        private final ActorRef<Receptionist.Listing> listingResponseAdapter;

        public WaitingForUIFinishedBehaviour(ActorContext<Responder.Command> context) {
            super(context);
            this.listingResponseAdapter = context.messageAdapter(Receptionist.Listing.class, Responder.ListingResponse::new);
        }

        private Behavior<Responder.Command> onUIFinished(Responder.UIFinished msg) {
            getContext()
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.find(Surveyor.surveyorServiceKey, listingResponseAdapter));

            return new WaitingForListingBehaviour(getContext(), msg.results);
        }

        @Override
        public Receive<Responder.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Responder.UIFinished.class, this::onUIFinished)
                    .build();
        }
    }

    static class WaitingForListingBehaviour extends AbstractBehavior<Responder.Command> {
        private final SurveyResult surveyResults;

        public WaitingForListingBehaviour(ActorContext<Responder.Command> context, SurveyResult surveyResults) {
            super(context);
            this.surveyResults = surveyResults;
        }

        private Behavior<Responder.Command> onListing(Responder.ListingResponse msg) {
            msg.listing.getServiceInstances(Surveyor.surveyorServiceKey)
                    .forEach(surveyorService -> surveyorService.tell(new Surveyor.ReceiveSurveyResult(surveyResults)));
            return Behaviors.empty();
        }

        @Override
        public Receive<Responder.Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Responder.ListingResponse.class, this::onListing)
                    .build();
        }
    }
}
