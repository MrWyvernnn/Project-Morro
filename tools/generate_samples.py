import csv
import gzip
import io
import os

m1_data = [
    # PuzzleId, FEN, Moves, Rating, Themes
    ("00sHx", "q3k1nr/1pp1nQpp/3p4/1P2p3/4P3/1B1P1b2/2PB1P1P/1N2K1R1 b k - 0 1", "e8d7 b3e6", 620, "mateIn1 short"),
    ("01aB2", "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 5", "c4f7", 650, "mateIn1 short"),
    ("02cDef", "r1b1k2r/ppppqppp/2n5/1B2p3/4n3/5N2/PPPP1PPP/R1BQK2R w KQkq - 0 7", "b5c6", 700, "mateIn1 short"),
    ("03gHi", "6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1", "e1e8", 610, "mateIn1 backRankMate short"),
    ("04jKl", "r4rk1/5ppp/8/8/8/8/5PPP/4R1K1 b - - 0 1", "f8e8 e1e8", 630, "mateIn1 backRankMate short"),
    ("05mNo", "r1bqk2r/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/R1B1K1NR w KQkq - 0 6", "d2d3 f3f7", 640, "mateIn1 short"),
    ("06pQr", "rnbqkbnr/ppppp2p/5p2/6p1/3PP3/8/PPP2PPP/RNBQKBNR w KQkq - 0 3", "c2c3 d1h5", 615, "mateIn1 short"),
    ("07sTu", "r1b2rk1/2p2ppp/p7/1p6/3P3q/1BP2Q1P/PP3PP1/RNB1R1K1 w - - 0 16", "c1e3 f3f7", 750, "mateIn1 short"),
    ("08vWx", "r1bqkb1r/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 4", "d7d6 h5f7", 630, "mateIn1 short"),
    ("09yZa", "5rk1/5ppp/8/8/8/8/1Q3PPP/6K1 w - - 0 1", "g8h8 b2g7", 680, "mateIn1 short"),
    ("10bCd", "3r2k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", "d8e8 d1e8", 605, "mateIn1 backRankMate short"),
    ("11eFg", "r1b1k2r/pppp1Npp/8/4p3/2Bn3q/6n1/PPPP3P/RNB2QKR b kq - 1 10", "d4e2 g1g2 h4h3", 820, "mateIn1 short"),
    ("12hIj", "6k1/p4ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", "g8f8 a1a8", 612, "mateIn1 backRankMate short"),
    ("13kLm", "r3k2r/ppp2ppp/2n5/3p4/3P4/B1PB1N2/P4PPP/R4RK1 b kq - 0 12", "e8d8 f1e1", 790, "mateIn1 short"),
    ("14nOp", "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4", "c5b6 f3f7", 655, "mateIn1 short"),
    ("15qRs", "2kr3r/ppp2ppp/8/8/1b1n4/2N5/PPPB1PPP/R3K2R b KQ - 1 12", "h8e8 e1d1 d4c2", 840, "mateIn1 short"),
    ("16tUv", "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", "d7d6 h5f7", 625, "mateIn1 short"),
    ("17wXy", "6k1/5ppp/8/8/8/8/5PPP/1r4K1 w - - 0 1", "g1f1 b1f1", 600, "mateIn1 backRankMate short"),
    ("18zAb", "r1b1k2r/ppppqppp/2n5/4p3/2B1n3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 6", "c4f7 e8f7", 710, "mateIn1 short"),
    ("19cDe", "4r1k1/5ppp/8/8/8/8/4QPPP/6K1 w - - 0 1", "g8f8 e2e8", 618, "mateIn1 backRankMate short"),
    ("20fGh", "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "g8f6 f1c4", 650, "mateIn1 short"),
    ("21iJk", "2r3k1/5ppp/8/8/8/8/5PPP/2R3K1 w - - 0 1", "g8h8 c1c8", 608, "mateIn1 backRankMate short"),
    ("22lMn", "r1bqkb1r/1ppp1ppp/p1n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 5", "g8f6 h5f7", 635, "mateIn1 short"),
    ("23oPq", "3r2k1/p4ppp/8/8/8/8/PP3PPP/3R2K1 w - - 0 1", "g8h8 d1d8", 611, "mateIn1 backRankMate short"),
    ("24rSt", "6k1/5ppp/8/8/8/8/r4PPP/4R1K1 w - - 0 1", "g1f1 a2a1 e1a1", 620, "mateIn1 backRankMate short"),
]

m2_data = [
    # PuzzleId, FEN, Moves, Rating, Themes
    ("100aB", "6k1/5ppp/8/8/8/8/4qPPP/6K1 w - - 0 1", "g1h1 e2e1 f2f1 e1f1", 1050, "mateIn2 short"),
    ("101cD", "r1bqk2r/ppppbppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 2 5", "g8f6 f3f7 e8d8 f7e8", 1100, "mateIn2 short"),
    ("102eF", "6k1/5ppp/8/8/8/8/5PPP/1R4K1 w - - 0 1", "g8f8 b1b8 f8e7 b8e8", 1080, "mateIn2 backRankMate short"),
    ("103gH", "r1b2rk1/ppp2ppp/8/4N3/2B5/8/PPP2PPP/R1B1K2R w KQ - 1 12", "f8e8 c4f7 g8h8 f7e8", 1120, "mateIn2 short"),
    ("104iJ", "r4rk1/1pp2ppp/p7/4N3/1b6/8/PPP2PPP/R1B1K2R w KQ - 1 13", "a8e8 e1d1 e8e1", 1150, "mateIn2 short"),
    ("105kL", "3r2k1/5ppp/8/8/8/8/1R3PPP/6K1 w - - 0 1", "g8f8 b2b8 d8b8 a2a3 b8b1", 1090, "mateIn2 backRankMate short"),
    ("106mN", "r2qkb1r/pp1bpppp/2n5/1B1p4/3Pn3/5N2/PPP2PPP/RNBQK2R w KQkq - 3 7", "e8e7 b5c6 d7c6 d1e2", 1180, "mateIn2 short"),
    ("107oP", "2r3k1/5ppp/8/8/8/8/1Q3PPP/6K1 w - - 0 1", "g8f8 b2b8 c8b8 g1f1 b8b1", 1060, "mateIn2 backRankMate short"),
    ("108qR", "r1b1k2r/pppp1ppp/2n5/4p3/2B1n3/5N2/PPPP1PPP/RNB1K2R w KQkq - 0 7", "e4f2 e1f2 f7f6 f2g1", 1210, "mateIn2 short"),
    ("109sT", "6k1/5ppp/8/8/8/8/r4PPP/1R4K1 w - - 0 1", "g8f8 b1b8 f8e7 b8e8", 1070, "mateIn2 backRankMate short"),
    ("110uV", "r1bqk2r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 5", "d7d5 f3f7 e8d8 f7d5", 1130, "mateIn2 short"),
    ("111wX", "4r1k1/5ppp/8/8/8/8/4RPPP/6K1 w - - 0 1", "g8f8 e2e8 f8e8 g1f1", 1040, "mateIn2 backRankMate short"),
    ("112yZ", "r1b2rk1/2p2ppp/p7/1p6/3P4/1BP5/PP3PPb/RNB1R2K w - - 0 17", "h2h3 g1h1 h3g2", 1250, "mateIn2 short"),
    ("113aC", "5rk1/5ppp/8/8/8/8/5PPP/R3R1K1 w - - 0 1", "f8e8 e1e8 g8f8 a1a8", 1110, "mateIn2 backRankMate short"),
    ("114eG", "r1bqkb1r/pppp1ppp/2n5/4p3/2B1n3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 5", "g8f6 f3f7 e8d8 f7d5", 1125, "mateIn2 short"),
    ("115iK", "3r2k1/p4ppp/8/8/8/8/P4PPP/1R4K1 w - - 0 1", "g8f8 b1b8 d8b8 h2h3 b8b1", 1075, "mateIn2 backRankMate short"),
    ("116mO", "r1b1k2r/ppppqppp/2n5/4p3/2B1n3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 6", "c4f7 e8f7 d1h5", 1190, "mateIn2 short"),
    ("117qS", "6k1/p4ppp/8/8/8/8/5PPP/1R2R1K1 w - - 0 1", "g8f8 b1b8 f8e7 e1e8", 1085, "mateIn2 backRankMate short"),
]

def write_csv_gz(filename, data):
    fields = ["PuzzleId", "FEN", "Moves", "Rating", "Themes"]
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(fields)
    for row in data:
        writer.writerow(row)
    
    raw = buf.getvalue().encode("utf-8")
    with gzip.open(filename, "wb") as f:
        f.write(raw)
    print(f"Wrote {len(data)} rows to {filename} ({len(raw)} bytes uncompressed, {os.path.getsize(filename)} bytes compressed)")

os.makedirs("app/src/main/assets/puzzles", exist_ok=True)
write_csv_gz("app/src/main/assets/puzzles/mate_in_1.csv.gz", m1_data)
write_csv_gz("app/src/main/assets/puzzles/mate_in_2.csv.gz", m2_data)
