package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import net.music.speaker.models.SurveyResult;

public class Responder {
    interface Command {}
    public static final class OpenSurvey implements Command {}
    public static final class UIFinished implements Command {}

    static final class ListingResponse implements Command {
        final Receptionist.Listing listing;

        ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(WaitingForOpenSurveyBehaviour::new);
    }

    static class WaitingForOpenSurveyBehaviour extends AbstractBehavior<Responder.Command> {
        public WaitingForOpenSurveyBehaviour(ActorContext<Responder.Command> context) {
            super(context);
        }

        private Behavior<Responder.Command> onOpenSurvey(Responder.OpenSurvey msg) {
            getContext().getLog().info("Survey Open!");

            // ... tu jest UI, ktore po zamknieciu zrobi ...
            // ui = new Window();
            // ui.onFinished(() -> this.context.getSelf().tell(new UIFinished()));
            // ui.start();
            getContext().getSelf().tell(new Responder.UIFinished());

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
            // ... wczytaj z UI ...
            SurveyResult surveyResults = new SurveyResult();
            surveyResults.test = "1234";

            getContext()
                    .getSystem()
                    .receptionist()
                    .tell(Receptionist.find(Surveyor.surveyorServiceKey, listingResponseAdapter));

            return new WaitingForListingBehaviour(getContext(), surveyResults);
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
