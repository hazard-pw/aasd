package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Main extends AbstractBehavior<Void> {

    private final ActorRef<Responder.Command> responder;
    private final ActorRef<Surveyor.Command> surveyor;
    private final ActorRef<Speaker.Command> speaker;

    public Main(ActorContext<Void> context) {
        super(context);
        responder = context.spawn(Responder.create(), "responder");
        responder.tell(new Responder.OpenSurvey());
        surveyor = context.spawn(Surveyor.create(), "surveyor");
        speaker = context.spawn(Speaker.create(), "speaker");
        speaker.tell(new Speaker.PlaySongRequest("spotify:playlist:4tu64dqBn5bzBZ3o1qiabJ?si=f441c545421e45d5"));
    }

    public static Behavior<Void> create() {
        return Behaviors.setup(Main::new);
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().build();
    }

}
