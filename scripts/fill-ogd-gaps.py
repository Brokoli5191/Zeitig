#!/usr/bin/env python3
"""Fill DIVA/municipality/coordinate gaps in a fresh Wiener Linien stop-point CSV from an older one.

Upstream snapshots occasionally publish rows like `299;;Raxstraße;;;0000000000;0000000000` for stop
points that had full data before. Shipping such a snapshot would make those stops unlocatable, so
the refresh workflow keeps the last known values. Usage: fill-ogd-gaps.py OLD.csv NEW.csv
(NEW is rewritten in place, CRLF line endings preserved). Prints the number of filled rows.
"""
import sys

UNKNOWN = {"", "0000000000"}


def read(path):
    with open(path, encoding="utf-8", newline="") as f:
        return [line.rstrip("\r\n") for line in f if line.strip()]


def main(old_path, new_path):
    old_rows = read(old_path)
    new_rows = read(new_path)
    header = new_rows[0].lstrip("﻿").split(";")
    col = {name: i for i, name in enumerate(header)}
    old_header = old_rows[0].lstrip("﻿").split(";")
    old_col = {name: i for i, name in enumerate(old_header)}
    old_by_id = {}
    for row in old_rows[1:]:
        fields = row.split(";")
        old_by_id[fields[old_col["StopID"]]] = fields

    filled = 0
    out = [new_rows[0]]
    for row in new_rows[1:]:
        fields = row.split(";")
        old = old_by_id.get(fields[col["StopID"]])
        if old is not None:
            changed = False
            for name in ("DIVA", "Municipality", "MunicipalityID"):
                if fields[col[name]] in UNKNOWN and old[old_col[name]] not in UNKNOWN:
                    fields[col[name]] = old[old_col[name]]
                    changed = True
            if fields[col["Latitude"]] in UNKNOWN or fields[col["Longitude"]] in UNKNOWN:
                if old[old_col["Latitude"]] not in UNKNOWN and old[old_col["Longitude"]] not in UNKNOWN:
                    fields[col["Latitude"]] = old[old_col["Latitude"]]
                    fields[col["Longitude"]] = old[old_col["Longitude"]]
                    changed = True
            filled += changed
        out.append(";".join(fields))

    with open(new_path, "w", encoding="utf-8", newline="") as f:
        f.write("\r\n".join(out) + "\r\n")
    print(filled)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
