import { useEffect, useMemo, useState } from "react";
import { Panel } from "./Panel";
import { Alert, Button, Slider, Typography, Box, MenuItem, Select, Chip } from "@mui/material";
import { Formik } from 'formik';
import { VotePanel } from "./VotePanel";

const connectionStatuses = {
    connecting: {
        text: "Connecting to backend..",
        color: "info"
    },
    connected: {
        text: "Connected to backend",
        color: "success"
    },
    closed: {
        text: "Connection closed",
        color: "error"
    },
    error: {
        text: "Connection error",
        color: "error"
    }
}

const defaultPreferences = {
    "genres": ["hip-hop"],
    "danceability": 0,
    "valence": 0,
    "energy": 0,
    "tempo": 0,
    "speechiness": 0,
}

const availableGenres = [
    "acoustic", "afrobeat", "alt-rock", "alternative",
    "ambient", "anime", "black-metal", "bluegrass", "blues",
    "bossanova", "brazil", "breakbeat", "british", "cantopop",
    "chicago-house", "children", "chill", "classical", "club",
    "comedy", "country", "dance", "dancehall", "death-metal", "deep-house",
    "detroit-techno", "disco", "disney", "drum-and-bass", "dub", "dubstep", "edm",
    "electro", "electronic", "emo", "folk", "forro", "french", "funk",
    "garage", "german", "gospel", "goth", "grindcore",
    "groove", "grunge", "guitar", "happy", "hard-rock",
    "hardcore", "hardstyle", "heavy-metal", "hip-hop", "holidays",
    "honky-tonk", "house", "idm", "indian", "indie", "indie-pop",
    "industrial", "iranian", "j-dance", "j-idol", "j-pop", "j-rock", "jazz", "k-pop",
    "kids", "latin", "latino", "malay", "mandopop", "metal", "metal-misc", "metalcore",
    "minimal-techno", "movies", "mpb", "new-age", "new-release", "opera", "pagode",
    "party", "philippines-opm", "piano", "pop", "pop-film", "post-dubstep", "power-pop",
    "progressive-house", "psych-rock", "punk", "punk-rock", "r-n-b", "rainy-day", "reggae",
    "reggaeton", "road-trip", "rock", "rock-n-roll", "rockabilly", "romance", "sad", "salsa", "samba",
    "sertanejo", "show-tunes", "singer-songwriter", "ska", "sleep", "songwriter", "soul", "soundtracks", "spanish",
    "study", "summer", "swedish", "synth-pop", "tango", "techno", "trance", "trip-hop", "turkish", "work-out", "world-music"
];

const ITEM_HEIGHT = 48;
const ITEM_PADDING_TOP = 8;
const MenuProps = {
  PaperProps: {
    style: {
      maxHeight: ITEM_HEIGHT * 4.5 + ITEM_PADDING_TOP,
      width: 250,
    },
  },
};

export const Dashboard = ({ address, showPreferences }) => {
    const [preferencesSaved, setPreferencesSaved] = useState(!showPreferences)
    const [votingActive, setVotingActive] = useState(false);
    const [votingExpireTimestamp, setVotingExpireTimestamp] = useState(false);
    const [voted, setVoted] = useState(false);
    const [connectionStatus, setConnectionStatus] = useState("connecting")

    const socket = useMemo(() => {
        const socket = new WebSocket(`ws://${window.location.host}/ws`);

        socket.addEventListener('open', () => setConnectionStatus("connected"));
        socket.addEventListener('close', () => setConnectionStatus("closed"));
        socket.addEventListener('error', () => setConnectionStatus("error"));
        socket.addEventListener('message', (event) => {
            const payload = JSON.parse(event.data);
            switch (payload.action) {
                case "voteStarted":
                    setVotingActive(true)
                    setVotingExpireTimestamp(payload.expireTimestamp)
                    break;
                case "voteFinished":
                    setVotingActive(false)
                    setVoted(false)
                    break;
            }
        });

        return socket;
    }, [])

    const handleSavePreferences = (data) => {
        setPreferencesSaved(true);
        socket.send(JSON.stringify({
            action: "setPreferences",
            value: {
                ...data,
                artists: [],
                tracks: []
            }
        }))
    }

    const handleVote = (option) => {
        setVoted(true)

        socket.send(JSON.stringify({
            action: "vote",
            value: option
        }))
    }

    return <div>
        {connectionStatus === "connected" && <div>
            {preferencesSaved && <VotePanel
                votingActive={votingActive}
                votingExpireTimestamp={votingExpireTimestamp}
                voted={voted}
                onRequestVoting={() => handleVote(true)}
                onVote={handleVote} />
            }

            {!preferencesSaved &&
                <Panel title="Preferences">
                    <Formik onSubmit={handleSavePreferences} initialValues={defaultPreferences}>
                        {({ values, handleSubmit, setFieldValue }) => (
                            <form onSubmit={handleSubmit}>
                                    <Select
                                        multiple
                                        value={values['genres']}
                                        onChange={({target}) => {
                                            setFieldValue('genres', target.value)
                                        }}
                                        renderValue={(selected) => (
                                            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                                                {selected.map((value) => (
                                                    <Chip key={value} label={value} />
                                                ))}
                                            </Box>
                                        )}
                                        MenuProps={MenuProps}
                                    >
                                        {availableGenres.map((genre) => (
                                            <MenuItem
                                                key={genre}
                                                value={genre}
                                            >
                                                {genre}
                                            </MenuItem>
                                        ))}
                                    </Select>
                                {Object.keys(defaultPreferences).filter(k => k !== 'genres').map(entry =>
                                    <div key={entry}>
                                        <div>
                                            <Typography>{entry}</Typography>
                                            <Slider
                                                valueLabelDisplay="auto"
                                                value={values[entry]}
                                                onChange={(e, v) => setFieldValue(entry, v)}
                                                step={entry === 'tempo' ? 10 : 0.1}
                                                min={entry === 'tempo' ? 50 : 0}
                                                max={entry === 'tempo' ? 200 : 1}
                                            />
                                        </div>
                                    </div>)}

                                <Button variant="contained" type="submit">Save</Button>
                            </form>
                        )}
                    </Formik>
                </Panel>
            }
        </div>
        }
        <Alert severity={connectionStatuses[connectionStatus].color}>
            {connectionStatuses[connectionStatus].text}
        </Alert>
        <Alert severity="info">
            Local cluster: {address}
        </Alert>
    </div>
}