package net.music.speaker.agents;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.music.speaker.models.SurveyResult;

public class Surveyor extends AbstractBehavior<Surveyor.Command> {

    public static final ServiceKey<Command> surveyorServiceKey =
            ServiceKey.create(Command.class, "surveyor");

    interface Command {}

    public static final class ReceiveSurveyResult implements Command {
        final SurveyResult result;

        public ReceiveSurveyResult(SurveyResult result) {
            this.result = result;
        }
    }

    public Surveyor(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(
                context -> {
                    context
                            .getSystem()
                            .receptionist()
                            .tell(Receptionist.register(surveyorServiceKey, context.getSelf()));

                    return new Surveyor(context);
                });
    }

    private Behavior<Command> onSurveyResult(ReceiveSurveyResult msg) {
        System.out.println(msg.result.test);
        return this;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ReceiveSurveyResult.class, this::onSurveyResult)
                .build();
    }

}
