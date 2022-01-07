import {Panel} from "./Panel";
import {Alert, Button} from "@mui/material";
import {useTimer} from "react-timer-hook";
import {useEffect} from "react";

export const VotePanel = ({votingActive, votingExpireTimestamp, onRequestVoting, onVote, voted}) => {
    const {minutes, seconds, restart} = useTimer({ expiryTimestamp: new Date(), autoStart: false });

    useEffect(() => {
        if (votingActive && votingExpireTimestamp) {
            restart(new Date(votingExpireTimestamp));
        }
    }, [votingActive, votingExpireTimestamp])

    return (
        <div>
            {votingActive ? <Panel title="Do you want to skip song?">
                {voted ? <Alert severity="info">Waiting for vote result</Alert> : <>
                    <div>
                        <Button variant="contained" color="success" onClick={() => onVote(true)}>Yes</Button>
                        <Button variant="contained" color="error" onClick={() => onVote(false)}>No</Button>
                    </div>
                </>}
                <div style={{marginTop: "10px"}}>
                    Ends in: {String(minutes).padStart(2, '0')}:{String(seconds).padStart(2, '0')}
                </div>
            </Panel> : <Panel title="Voting">
                <Button variant="contained" onClick={() => onRequestVoting()}>Request song skip</Button>
            </Panel>}
        </div>
    )
}