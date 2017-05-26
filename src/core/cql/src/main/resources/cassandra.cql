
CREATE TABLE partition (
    interval text,
    start timestamp,
    end timestamp,
    tables set<text>,
    PRIMARY KEY (interval,start,end)
);
