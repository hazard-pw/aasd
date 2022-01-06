package net.music.speaker.models;

public class SurveyResult {
    // Mood
    public float danceability;
    public float valence;
    public float energy;
    public float tempo;

    // Properties
    public float loudness;
    public float speechiness;
    public float instrumentalness;

    // Context
    public float liveness;
    public float acousticness;

    // Seeds
    // You need to pick at least one in total (e.g. 1 genre, 0 artists, 0 tracks is fine)
    // You need to pick at most 5 in every category
    // (these are the requirements for the recommendations API)
    public String[] artists; // spotify IDs
    public String[] genres; // see https://developer.spotify.com/console/get-available-genre-seeds/
    public String[] tracks; // spotify IDs

    public boolean isValid() {
        if (danceability < 0 || danceability > 1)
            return false;
        if (valence < 0 || valence > 1)
            return false;
        if (energy < 0 || energy > 1)
            return false;
        if (tempo < 0 || tempo > 1)
            return false;
        if (loudness < 0 || loudness > 1)
            return false;
        if (speechiness < 0 || speechiness > 1)
            return false;
        if (instrumentalness < 0 || instrumentalness > 1)
            return false;
        if (liveness < 0 || liveness > 1)
            return false;
        if (acousticness < 0 || acousticness > 1)
            return false;

        if (artists.length + genres.length + tracks.length < 1)
            return false;
        if (artists.length > 5)
            return false;
        if (genres.length > 5)
            return false;
        if (tracks.length > 5)
            return false;

        return true;
    }

    @Override
    public String toString() {
        return "SurveyResult{" +
                "danceability=" + danceability +
                ", valence=" + valence +
                ", energy=" + energy +
                ", tempo=" + tempo +
                ", loudness=" + loudness +
                ", speechiness=" + speechiness +
                ", instrumentalness=" + instrumentalness +
                ", liveness=" + liveness +
                ", acousticness=" + acousticness +
                '}';
    }
}
