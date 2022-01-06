export const Panel = ({title, children}) => {
    return (
        <div className="panel">
            <div className="box-title">
                {title}
            </div>
            <div className="box">
                {children}
            </div>
        </div>
    )
}