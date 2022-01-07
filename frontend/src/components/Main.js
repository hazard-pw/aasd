import useAxios from "axios-hooks";
import {Dashboard} from "./Dashboard";
import {Alert} from "@mui/material";
import {Session} from "./Session";

export const Main = () => {
    const [{data, loading, error}, refetch] = useAxios(
        window.location.origin + '/api/status'
    )

    const handleJoin = () => {
        refetch();
    }

    if (loading || error) {
        return <>
            {loading && <Alert severity="info">Checking session status</Alert>}
            {error && <Alert severity="error">Something went wrong</Alert>}
        </>
    }

    return data?.inSession ? <Dashboard address={data?.address} showPreferences={!data?.preferencesSet}/> : <Session onJoin={handleJoin}/>
}
