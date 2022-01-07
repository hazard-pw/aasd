import {useState} from "react";
import axios from "axios";
import {Panel} from "./Panel";
import {Button, TextField} from "@mui/material";

const instance = axios.create({
    baseURL: window.location.origin
})

export const Session = ({onJoin}) => {
    const [ip, setIp] = useState("");

    const handleCreateSession = async () => {
        const response = await instance.post("/api/createSession", {})
        window.location.replace(response.data.authUri);
    }

    const handleJoinSession = async () => {
        await instance.post("/api/joinSession", {
            value: ip
        })

        onJoin()
    }

    return (
        <div>
            <Panel title="New session">
                <Button variant="contained" onClick={() => handleCreateSession()}>
                    Create session
                </Button>
            </Panel>
            <Panel title="Existing session">
                <div style={{marginBottom: "10px"}}>
                    <TextField
                        label="Address"
                        variant="outlined"
                        value={ip}
                        onChange={(event) => setIp(event.target.value)}
                    />
                </div>
                <Button variant="contained" onClick={() => handleJoinSession()}>
                    Join session
                </Button>
            </Panel>
        </div>
    )
}