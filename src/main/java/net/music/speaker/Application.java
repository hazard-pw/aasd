package net.music.speaker;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.japi.Pair;
import net.music.speaker.agents.Main;
import akka.http.javadsl.Http;
import se.michaelthelin.spotify.Base64;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;


public class Application extends AllDirectives {
    private final ActorSystem<Void> system;
    private final Http http;

    public Application(ActorSystem<Void> system, Http http) {
        this.system = system;
        this.http = http;
    }

    public static void main(String[] args) throws Exception {
        final ActorSystem<Void> system = ActorSystem.create(Main.create(), "root");

        final Http http = Http.get(system);
        Application app = new Application(system, http);

        final CompletionStage<ServerBinding> binding = http.newServerAt("localhost", 8080)
                .bind(app.createRoute());

        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read(); // let it run until user presses return

        binding
                .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }


    private Route createRoute() {
        return concat(
                path("login", () -> get(this::login)),
                path("callback", () -> get(this::loginCallback))
            );
    }

    private static final String CLIENT_ID = "eb4ae8cd60674701a3bb88b33ca045eb";
    private static final String CLIENT_SECRET = "5f158164842a43d8a796eecdfb475b7a";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String SCOPE = "user-read-private+user-read-email+user-read-playback-state+user-modify-playback-state+user-read-currently-playing";


    private Route login() {
        Uri uri = Uri.create(String.format("https://accounts.spotify.com/authorize?" +
                "response_type=code" +
                "&client_id=%s" +
                "&scope=%s" +
                "&redirect_uri=%s",
                CLIENT_ID,
                SCOPE,
                REDIRECT_URI
        ));
        return redirect(uri, StatusCodes.FOUND);
    }
    // TODO:
    // onCallback -> create new Participant with Speaker possibilities
    // join website -> register new Participant
    // participant has its participant id
    // global moderator, surveyor
    // SpeakerManager by musiał być

    private Route loginCallback() {
        return parameterMap((params) -> {
            try {
                AuthResponse authResponse = http.singleRequest(HttpRequest.create("https://accounts.spotify.com/api/token")
                        .withMethod(HttpMethods.POST)
                        .withHeaders(List.of(
                                HttpHeader.parse("Authorization", "Basic " + Base64.encode((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8))),
                                HttpHeader.parse("Accept", "application/json")
                        ))
                        .withEntity(FormData.create(
                                Pair.create("code", params.get("code")),
                                Pair.create("redirect_uri", REDIRECT_URI),
                                Pair.create("grant_type", "authorization_code")
                        ).toEntity()))
                        .thenApply((response) -> Jackson.unmarshaller(AuthResponse.class).unmarshal(response.entity(), system))
                        .toCompletableFuture()
                        .get()
                        .toCompletableFuture()
                        .get();

                return complete(authResponse.toString());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public record AuthResponse(String access_token, String token_type, String scope, Integer expires_in, String refresh_token) {}
}
