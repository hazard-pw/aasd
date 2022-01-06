package net.music.speaker;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
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
import java.util.concurrent.CompletionStage;


public class Application extends AllDirectives {

    private static final String CLIENT_ID = "eb4ae8cd60674701a3bb88b33ca045eb";
    private static final String CLIENT_SECRET = "5f158164842a43d8a796eecdfb475b7a";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String SCOPE = "user-read-playback-state,user-modify-playback-state,user-read-currently-playing";

    public final ActorSystem<Main.Command> system;
    public final Http http;
    public final SpotifyApi spotifyApi;

    public static void main(String[] args) {
        new Application();
    }

    public Application() {
        this.system = ActorSystem.create(Main.create(), "root");
        this.http = Http.get(system);

        this.spotifyApi = new SpotifyApi.Builder()
            .setClientSecret(CLIENT_SECRET)
            .setClientId(CLIENT_ID)
            .setRedirectUri(URI.create(REDIRECT_URI))
            .build();

        http.newServerAt("localhost", 8080)
            .bind(concat(
                path("", () -> get(this::index)),
                path("callback", () -> get(this::loginCallback))
            ));

        System.out.println("Server online at http://localhost:8080/");
        openBrowser(URI.create("http://localhost:8080/"));
    }

    private Route index() {
        if (spotifyApi.getAccessToken() == null) {
            URI uri = spotifyApi.authorizationCodeUri()
                    .scope(SCOPE)
                    .build()
                    .execute();
            return redirect(Uri.create(uri.toString()), StatusCodes.FOUND);
        } else {
            return complete("Welcome to the Awesome Agent System for Deciding what music to play\n" + system.printTree());
        }
    }

    // TODO:
    // onCallback -> create new Participant with Speaker possibilities
    // join website -> register new Participant
    // participant has its participant id
    // global moderator, surveyor
    // SpeakerManager by musiał być

    private Route loginCallback() {
        if (spotifyApi.getAccessToken() != null) {
            system.log().warn("What are you doing, you already have a token");
            return redirect(Uri.create("/"), StatusCodes.FOUND);
        }

        return parameterMap((params) -> {
            try {
                AuthorizationCodeCredentials credentials = spotifyApi.authorizationCode(params.get("code"))
                        .build()
                        .execute();

                spotifyApi.setAccessToken(credentials.getAccessToken());
                spotifyApi.setRefreshToken(credentials.getRefreshToken());

                system.tell(new Main.StartNewSession(spotifyApi));

                return redirect(Uri.create("/"), StatusCodes.FOUND);
            } catch (ParseException | SpotifyWebApiException | IOException e) {
                throw new RuntimeException(e);
            }
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
