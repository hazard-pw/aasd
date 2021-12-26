package net.music.speaker.agents;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.google.gson.JsonArray;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;

import java.io.IOException;
import java.net.URI;

public class Speaker extends AbstractBehavior<Speaker.Command> {
    private static final String CLIENT_ID = "eb4ae8cd60674701a3bb88b33ca045eb";
    private static final String CLIENT_SECRET = "5f158164842a43d8a796eecdfb475b7a";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";

    private final SpotifyApi spotifyApi;

    interface Command { }

    public record PlaySongRequest(String songURI) implements Command {}
    public record SkipSongRequest(String songURI) implements Command {}

    public Speaker(ActorContext<Command> context) {
        super(context);
        // TODO: create speaker based on callback request
        spotifyApi = new SpotifyApi.Builder()
                .setClientSecret(CLIENT_SECRET)
                .setClientId(CLIENT_ID)
                .setRedirectUri(URI.create(REDIRECT_URI))
                .setAccessToken("BQD16NOJf9JH5i5Qx5VDpljmisVq2oOu4a3zNnJyvp7ijBq7ONCwkyCSYFu12FO_-6qdUs9FPFHIj_9nyviz0r0irHtg4DC5ZTEE1lS-nGQuBQ-Y0pFxL_OrmoUjtcOAKTR-IKQ0d69bVw07faqiKwhouEtlPKhEbQ")
                .setRefreshToken("AQDXdT2Dsdiy-Wh4grxAmnW3jD86FNSY0fXezLGN4KKx0AAtUHYdFol6ogB9CuE2OC4tEnxD-JDO9SBKtHFNfj9YVtlVSGlisJqWskGntABBQDLKBgLTC4YOkpQi3ywGYKE")
                .build();
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(Speaker::new);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PlaySongRequest.class, this::playSong)
                .onMessage(SkipSongRequest.class, this::skipSong)
                .build();
    }

    private AbstractBehavior<Command> playSong(PlaySongRequest request) {
        try {
            Device[] devices = spotifyApi.getUsersAvailableDevices().build().execute();
            String deviceId = null;
            for (Device device : devices) {
                if ("Computer".equals(device.getType())) {
                    deviceId = device.getId();
                    break;
                }
            }
            if (deviceId != null) {
//                JsonArray arr = new JsonArray();
//                arr.add(request.songURI);
                spotifyApi.startResumeUsersPlayback()
                        .device_id(deviceId)
                        .context_uri(request.songURI())
//                        .uris(arr)
                        .build()
                        .execute();
            }
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            getContext().getLog().error("Failed to play next song", e);
        }
        return this;
    }

    private AbstractBehavior<Command> skipSong(SkipSongRequest skipSongRequest) {
        try {
            spotifyApi.skipUsersPlaybackToNextTrack().build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            getContext().getLog().error("Failed to skip to the next song", e);
        }
        return this;
    }
}
