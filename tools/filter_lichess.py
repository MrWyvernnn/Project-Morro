#!/usr/bin/env python3
"""
tools/filter_lichess.py
Filter the full Lichess puzzle database CSV (CC0) for Morro alarm chess missions.

Usage:
    python3 tools/filter_lichess.py [path/to/lichess_db_puzzle.csv[.zst|.gz]]
    # or via stdin:
    cat lichess_db_puzzle.csv | python3 tools/filter_lichess.py

Outputs:
    app/src/main/assets/puzzles/mate_in_1.csv.gz
        - themes contains "mateIn1"
        - rating between 600 and 1200
        - target ~1200 rows
    app/src/main/assets/puzzles/mate_in_2.csv.gz
        - themes contains "mateIn2"
        - rating between 1000 and 1500
        - target ~800 rows

Columns kept: PuzzleId, FEN, Moves, Rating, Themes

Note:
    Sample gzipped CSVs are bundled initially for development and testing.
    Run this script once the full Lichess CSV is downloaded to replace samples with real filtered data.
"""

import sys
import os
import csv
import gzip
import io

OUTPUT_MATE_IN_1 = os.path.join("app", "src", "main", "assets", "puzzles", "mate_in_1.csv.gz")
OUTPUT_MATE_IN_2 = os.path.join("app", "src", "main", "assets", "puzzles", "mate_in_2.csv.gz")

TARGET_M1_COUNT = 1200
M1_MIN_RATING = 600
M1_MAX_RATING = 1200

TARGET_M2_COUNT = 800
M2_MIN_RATING = 1000
M2_MAX_RATING = 1500

FIELDS = ["PuzzleId", "FEN", "Moves", "Rating", "Themes"]

def open_input(source):
    if source is None or source == "-":
        return sys.stdin
    if source.endswith(".gz"):
        return gzip.open(source, "rt", encoding="utf-8")
    return open(source, "r", encoding="utf-8")

def main():
    source = sys.argv[1] if len(sys.argv) > 1 else "-"
    
    os.makedirs(os.path.dirname(OUTPUT_MATE_IN_1), exist_ok=True)
    os.makedirs(os.path.dirname(OUTPUT_MATE_IN_2), exist_ok=True)

    m1_rows = []
    m2_rows = []

    print(f"Reading from {source if source != '-' else 'stdin'}...")
    
    with open_input(source) as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                rating = int(row.get("Rating", 0))
            except ValueError:
                continue

            themes = row.get("Themes", "")
            theme_set = set(themes.split())

            clean_row = {
                "PuzzleId": row.get("PuzzleId", "").strip(),
                "FEN": row.get("FEN", "").strip(),
                "Moves": row.get("Moves", "").strip(),
                "Rating": str(rating),
                "Themes": themes.strip(),
            }

            if "mateIn1" in theme_set and M1_MIN_RATING <= rating <= M1_MAX_RATING:
                if len(m1_rows) < TARGET_M1_COUNT:
                    m1_rows.append(clean_row)

            if "mateIn2" in theme_set and M2_MIN_RATING <= rating <= M2_MAX_RATING:
                if len(m2_rows) < TARGET_M2_COUNT:
                    m2_rows.append(clean_row)

            if len(m1_rows) >= TARGET_M1_COUNT and len(m2_rows) >= TARGET_M2_COUNT:
                break

    def write_gz(path, rows):
        buf = io.StringIO()
        writer = csv.DictWriter(buf, fieldnames=FIELDS)
        writer.writeheader()
        for r in rows:
            writer.writerow(r)
        
        raw_bytes = buf.getvalue().encode("utf-8")
        uncompressed_size = len(raw_bytes)

        with gzip.open(path, "wb") as gz_out:
            gz_out.write(raw_bytes)

        compressed_size = os.path.getsize(path)
        return uncompressed_size, compressed_size

    uncomp_m1, comp_m1 = write_gz(OUTPUT_MATE_IN_1, m1_rows)
    print(f"Wrote {len(m1_rows)} rows to {OUTPUT_MATE_IN_1}")
    print(f"  Uncompressed size: {uncomp_m1} bytes, Compressed size: {comp_m1} bytes")

    uncomp_m2, comp_m2 = write_gz(OUTPUT_MATE_IN_2, m2_rows)
    print(f"Wrote {len(m2_rows)} rows to {OUTPUT_MATE_IN_2}")
    print(f"  Uncompressed size: {uncomp_m2} bytes, Compressed size: {comp_m2} bytes")

if __name__ == "__main__":
    main()
