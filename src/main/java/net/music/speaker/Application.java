package net.music.speaker;

import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.http.javadsl.server.AllDirectives;
import net.music.speaker.agents.Main;

public class Application extends AllDirectives {

    public final ActorSystem<Main.Command> system;
    public final Cluster cluster;
    public static WebServer webServer;

    public static void main(String[] args) {
        new Application(args.length > 0 ? Integer.parseInt(args[0]) : 8080);
    }

    public Application(int httpPort) {
        this.system = ActorSystem.create(Main.create(), "root");
        this.cluster = Cluster.get(system);

        webServer = new WebServer(httpPort, system, cluster);
        webServer.start();
    }

}
