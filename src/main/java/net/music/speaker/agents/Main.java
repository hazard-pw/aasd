package net.music.speaker.agents;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import se.michaelthelin.spotify.SpotifyApi;

import java.net.URI;

public class Main {
    public interface Command {}
    public record StartNewSession(SpotifyApi spotifyApi) implements Command {}
    public record JoinSession(String someSessionIDOrSomethingWeWillFigureItOutLater) implements Command {}

    public static Behavior<Main.Command> create() {
        return Behaviors.setup(AwaitStartBehavior::new);
    }

    public static class AwaitStartBehavior extends AbstractBehavior<Main.Command> {
        public AwaitStartBehavior(ActorContext<Command> context) {
            super(context);
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(StartNewSession.class, this::onStartNewSession)
                    .onMessage(JoinSession.class, this::onJoinSession)
                    .build();
        }

        private Behavior<Command> onStartNewSession(StartNewSession msg) {
            ActorRef<Responder.Command> responder = getContext().spawn(Responder.create(), "responder");
            ActorRef<Surveyor.Command> surveyor = getContext().spawn(Surveyor.create(msg.spotifyApi), "surveyor");
            ActorRef<Speaker.Command> speaker = getContext().spawn(Speaker.create(msg.spotifyApi), "speaker");
            ActorRef<Moderator.Command> moderator = getContext().spawn(Moderator.create(), "moderator");
            ActorRef<Skipper.Command> skipper = getContext().spawn(Skipper.create(), "skipper");

            responder.tell(new Responder.OpenSurvey());

            return Behaviors.empty();
        }

        private Behavior<Command> onJoinSession(JoinSession msg) {
            ActorRef<Responder.Command> responder = getContext().spawn(Responder.create(), "responder");
            ActorRef<Skipper.Command> skipper = getContext().spawn(Skipper.create(), "skipper");

            responder.tell(new Responder.OpenSurvey());

            return Behaviors.empty();
        }
    }
}
