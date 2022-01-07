import {Panel} from "./Panel";
import {Alert, Button} from "@mui/material";

export const VotePanel = ({votingActive, onRequestVoting, onVote, voted}) => {
    return (
        <div>
            {votingActive ? <Panel title="Do you want to skip song?">
                {voted ? <Alert severity="info">Waiting for vote result</Alert> : <>
                    <Button variant="contained" color="success" onClick={() => onVote(true)}>Yes</Button>
                    <Button variant="contained" color="error" onClick={() => onVote(false)}>No</Button>
                </>}
            </Panel> : <Panel title="Voting">
                <Button variant="contained" onClick={() => onRequestVoting()}>Request song skip</Button>
            </Panel>}
        </div>
    )
}