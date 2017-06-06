
CREATE TABLE partition (
    interval text,
    tag text,
    start timestamp,
    end timestamp,
    PRIMARY KEY (interval,tag,start,end)
);
