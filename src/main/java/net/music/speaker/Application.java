package net.music.speaker;

import akka.actor.typed.ActorSystem;
import net.music.speaker.agents.Main;

public class Application {

    public static void main(String[] args) {
        final ActorSystem<Void> system = ActorSystem.create(Main.create(), "root");
    }

}
