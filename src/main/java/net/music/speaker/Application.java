package net.music.speaker;

import akka.actor.AddressFromURIString;
import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import net.music.speaker.agents.Main;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import java.awt.*;
import java.io.IOException;
import java.net.URI;


public class Application extends AllDirectives {

    private static final String CLIENT_ID = "eb4ae8cd60674701a3bb88b33ca045eb";
    private static final String CLIENT_SECRET = "5f158164842a43d8a796eecdfb475b7a";
    private static final String SCOPE = "user-read-playback-state,user-modify-playback-state,user-read-currently-playing";

    public final ActorSystem<Main.Command> system;
    public final Cluster cluster;
    public final Http http;
    public final SpotifyApi spotifyApi;

    private boolean clusterStarted = false;

    public static void main(String[] args) {
        new Application(args.length > 0 ? Integer.parseInt(args[0]) : 8080);
    }

    public Application(int httpPort) {
        this.system = ActorSystem.create(Main.create(), "root");
        this.cluster = Cluster.get(system);
        this.http = Http.get(system);

        this.spotifyApi = new SpotifyApi.Builder()
            .setClientSecret(CLIENT_SECRET)
            .setClientId(CLIENT_ID)
            .setRedirectUri(URI.create("http://localhost:" + httpPort + "/callback"))
            .build();

        http.newServerAt("localhost", httpPort)
            .bind(concat(
                path("", () -> get(this::index)),
                path("callback", () -> get(this::loginCallback)),
                path("vote", () -> get(this::vote)),
                path("create_session", () -> get(this::createSession)),
                path("join_session", () -> get(this::joinSession))
            ));

        System.out.println("Server online at http://localhost:" + httpPort + "/");
        openBrowser(URI.create("http://localhost:" + httpPort + "/"));
    }

    private Route index() {
        if (!clusterStarted) {
            return complete(
                    HttpResponse.create().withEntity(ContentTypes.TEXT_HTML_UTF8,
"""
<h1>Welcome to the Awesome Agent System for Deciding what music to play</h1>

<form method="get" action="/create_session">
    <button type="submit">Create session</button>
</form>
<form method="get" action="/join_session">
    <input type="text" name="seed_ip" value=""/>
    <button type="submit">Join session</button>
</form>
"""
                    ));
        } else {
            return complete("Use this address to connect: " + cluster.selfMember().address().toString()+"\n\n"+system.printTree());
        }
    }

    private Route loginCallback() {
        if (clusterStarted)
            return redirect(Uri.create("/"), StatusCodes.FOUND);

        return parameterMap((params) -> {
            try {
                AuthorizationCodeCredentials credentials = spotifyApi.authorizationCode(params.get("code"))
                        .build()
                        .execute();

                spotifyApi.setAccessToken(credentials.getAccessToken());
                spotifyApi.setRefreshToken(credentials.getRefreshToken());

                cluster.manager().tell(Join.create(cluster.selfMember().address()));
                system.tell(new Main.StartNewSession(spotifyApi));
                clusterStarted = true;
                return redirect(Uri.create("/"), StatusCodes.FOUND);
            } catch (ParseException | SpotifyWebApiException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Route vote() {
        system.tell(new Main.VoteButton());
        return complete("Voted!");
    }

    private Route createSession() {
        if (clusterStarted)
            return redirect(Uri.create("/"), StatusCodes.FOUND);

        URI uri = spotifyApi.authorizationCodeUri()
                .scope(SCOPE)
                .build()
                .execute();
        return redirect(Uri.create(uri.toString()), StatusCodes.FOUND);
    }

    private Route joinSession() {
        if (clusterStarted)
            return redirect(Uri.create("/"), StatusCodes.FOUND);

        return parameterMap((params) -> {
            String seedIp = params.get("seed_ip");

            cluster.manager().tell(Join.create(AddressFromURIString.parse(seedIp)));
            system.tell(new Main.JoinSession());
            clusterStarted = true;

            return redirect(Uri.create("/"), StatusCodes.FOUND);
        });
    }

    private void openBrowser(URI url) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(url);
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Runtime runtime = Runtime.getRuntime();
        try {
            runtime.exec("xdg-open " + url.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
