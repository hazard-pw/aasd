package net.music.speaker;

import akka.actor.AddressFromURIString;
import akka.actor.typed.ActorSystem;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpCode;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import net.music.speaker.agents.Main;
import net.music.speaker.models.SurveyResult;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static io.javalin.apibuilder.ApiBuilder.*;

public class WebServer {

    record VoteRequest(String action, boolean value) {}
    record PreferencesRequest(String action, SurveyResult value) {}
    record JoinSessionRequest(String action, String value) {}
    record SpotifyAuthResponse(String authUri) {}
    record SessionStatusResponse(boolean inSession, boolean preferencesSet, String address) {}

    record ClusterResponse(String action, String errorMessage) {
        public ClusterResponse() {
            this("clusterResponse", "Already joined cluster");
        }
    }

    public record VoteStarted(String action, long expireTimestamp) {
        public VoteStarted(long expireTimestamp) {
            this("voteStarted", expireTimestamp);
        }
    }

    public record VoteFinished(String action) {
        public VoteFinished() {
            this("voteFinished");
        }
    }

    private final ActorSystem<Main.Command> system;
    private final Cluster cluster;
    private final int httpPort;
    private final List<WsContext> users;

    private boolean clusterStarted;
    private boolean preferencesSet;

    private final SpotifyApi spotifyApi;
    private static final String CLIENT_ID = "eb4ae8cd60674701a3bb88b33ca045eb";
    private static final String CLIENT_SECRET = "5f158164842a43d8a796eecdfb475b7a";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String SCOPE = "user-read-playback-state,user-modify-playback-state,user-read-currently-playing";

    public WebServer(int httpPort, ActorSystem<Main.Command> system, Cluster cluster) {
        this.httpPort = httpPort;
        this.cluster = cluster;
        this.system = system;
        this.users = new ArrayList<>();
        this.spotifyApi = new SpotifyApi.Builder()
                .setClientSecret(CLIENT_SECRET)
                .setClientId(CLIENT_ID)
                .setRedirectUri(URI.create(REDIRECT_URI))
                .build();
    }

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.addStaticFiles("frontend/build", Location.EXTERNAL);
            config.addSinglePageRoot("/", "frontend/build/index.html", Location.EXTERNAL);
            config.enableCorsForAllOrigins();
        }).start(httpPort);

        app.routes(() -> {
            get("/callback", ctx -> {
                if (spotifyApi.getAccessToken() != null) {
                    ctx.status(HttpCode.BAD_REQUEST);
                    return;
                }

                AuthorizationCodeCredentials credentials = spotifyApi.authorizationCode(ctx.queryParam("code"))
                        .build()
                        .execute();

                spotifyApi.setAccessToken(credentials.getAccessToken());
                spotifyApi.setRefreshToken(credentials.getRefreshToken());

                cluster.manager().tell(Join.create(cluster.selfMember().address()));
                system.tell(new Main.StartNewSession(spotifyApi));
                clusterStarted = true;
                ctx.redirect("/");
            });

            path("api", () -> {
                get("status", ctx -> {
                    ctx.json(new SessionStatusResponse(clusterStarted, preferencesSet, cluster.selfMember().address().toString()));
                });

                post("createSession", ctx -> {
                    if (clusterStarted) {
                        ctx.json(new ClusterResponse());
                        return;
                    }

                    URI uri = spotifyApi.authorizationCodeUri()
                            .scope(SCOPE)
                            .build()
                            .execute();

                    ctx.json(new SpotifyAuthResponse(uri.toString()));
                });

                post("joinSession", ctx -> {
                    if (clusterStarted) {
                        ctx.json(new ClusterResponse());
                        return;
                    }

                    JoinSessionRequest request = ctx.bodyAsClass(JoinSessionRequest.class);

                    cluster.manager().tell(Join.create(AddressFromURIString.parse(request.value)));
                    system.tell(new Main.JoinSession());
                    clusterStarted = true;
                });
            });

            ws("/ws", ws -> {
                ws.onConnect(users::add);
                ws.onMessage(ctx -> {
                    JsonNode jsonNode = new ObjectMapper().readTree(ctx.message());
                    switch (jsonNode.get("action").asText()) {
                        case "vote" -> {
                            VoteRequest request = ctx.messageAsClass(VoteRequest.class);
                            system.tell(new Main.VoteButton(request.value));
                        }
                        case "setPreferences" -> {
                            if (preferencesSet) return;

                            PreferencesRequest request = ctx.messageAsClass(PreferencesRequest.class);
                            system.tell(new Main.SetPreferences(request.value));
                            preferencesSet = true;
                        }
                    }
                });
            });
        });

        System.out.println("Server online at http://localhost:" + httpPort + "/");
        openBrowser(URI.create("http://localhost:" + httpPort + "/"));
    }

    public void sendBroadcast(Object data) {
        try {
            String payload = new ObjectMapper().writeValueAsString(data);

            users.stream().filter(ctx -> ctx.session.isOpen()).forEach(session -> {
                session.send(payload);
            });
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
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
