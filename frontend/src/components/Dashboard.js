import {useEffect, useMemo, useState} from "react";
import {Panel} from "./Panel";
import {Alert, Button, Slider, Typography} from "@mui/material";
import {Formik} from 'formik';
import {VotePanel} from "./VotePanel";

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
    "danceability": 0,
    "valence": 0,
    "energy": 0,
    "tempo": 0,
    "loudness": 0,
    "speechiness": 0,
    "instrumentalness": 0,
    "liveness": 0,
    "acousticness": 0
}

export const Dashboard = ({address, showPreferences}) => {
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
                genres: [],
                tracks: ["4qDHt2ClApBBzDAvhNGWFd"]
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
                onVote={handleVote}/>
            }

            {!preferencesSaved &&
            <Panel title="Preferences">
                <Formik onSubmit={handleSavePreferences} initialValues={defaultPreferences}>
                    {({values, handleSubmit, setFieldValue}) => (
                        <form onSubmit={handleSubmit}>
                            {Object.keys(defaultPreferences).map(entry =>
                                <div>
                                    <div>
                                        <Typography>{entry}</Typography>
                                        <Slider
                                            valueLabelDisplay="auto"
                                            value={values[entry]}
                                            onChange={(e, v) => setFieldValue(entry, v)}
                                            step={0.1}
                                            min={0}
                                            max={1}
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