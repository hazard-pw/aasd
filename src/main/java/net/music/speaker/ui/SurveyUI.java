package net.music.speaker.ui;

import net.music.speaker.models.SurveyResult;

import java.util.Scanner;

// TODO: implement actual UI over HTTP?
public class SurveyUI extends Thread {
    private final IOnSurveyUIResults callback;

    public interface IOnSurveyUIResults {
        void onSurveyUIResults(SurveyResult results);
    }

    public SurveyUI(IOnSurveyUIResults callback) {
        super();
        this.callback = callback;
    }

    @Override
    public void run() {
        SurveyResult results = new SurveyResult();

        printHeader("Mood");
        results.danceability = getValueFromUser("danceability");
        results.valence = getValueFromUser("valence");
        results.energy = getValueFromUser("energy");
        results.tempo = getValueFromUser("tempo");

        printHeader("Properties");
        results.loudness = getValueFromUser("loudness");
        results.speechiness = getValueFromUser("speechiness");
        results.instrumentalness = getValueFromUser("instrumentalness");

        printHeader("Context");
        results.liveness = getValueFromUser("liveness");
        results.acousticness = getValueFromUser("acousticness");

        // TODO: add seed selector
        results.artists = new String[] { };
        results.genres = new String[] { };
        results.tracks = new String[] { "4qDHt2ClApBBzDAvhNGWFd" };

        callback.onSurveyUIResults(results);
    }

    private float getValueFromUser(String prompt) {
        while(true) {
            System.out.print(prompt);
            System.out.print(" (0-1): ");
            Scanner input = new Scanner(System.in);
            try {
                float value = Float.parseFloat(input.nextLine().trim());
                if (value >= 0 && value <= 1)
                    return value;
            } catch (NumberFormatException e) {
                // retry
            }
        }
    }

    private void printHeader(String header) {
        System.out.println(header);
        for(int i = 0; i < header.length(); ++i)
            System.out.print("=");
        System.out.println();
    }
}
