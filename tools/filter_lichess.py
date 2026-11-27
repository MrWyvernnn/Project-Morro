#!/usr/bin/env python3
"""
tools/filter_lichess.py
Filter and programmatically VERIFY Lichess CC0 chess puzzles for Morro alarm gates.

Every candidate row is parsed and verified end-to-end before entering an asset:
  - Standard: theme contains "mateIn1", rating 1100..1600 (~1200 rows)
    * FEN parses
    * Moves[0] (opponent setup) is legal from FEN
    * Moves[1] is legal after Moves[0] AND delivers checkmate
  - Hard: theme contains "mateIn2", rating 1300..1800 (~800 rows)
    * FEN parses
    * Moves[0] is legal from FEN
    * Position after Moves[0] does NOT already have a mate-in-1
    * Moves[1] is legal AND not an immediate checkmate AND forces mate-in-2 across legal replies
    * Moves[2] is legal from the resulting position
    * Moves[3] is legal after Moves[2] AND delivers checkmate

Usage:
    # From full Lichess CSV (.csv or .gz) or stdin:
    python tools/filter_lichess.py [path/to/lichess_db_puzzle.csv[.gz]]

    # Or verify & re-filter directly from the bundled gzipped CC0 datasets:
    python tools/filter_lichess.py --verify-bundled
"""

import csv
import gzip
import io
import os
import sys

OUTPUT_MATE_IN_1 = os.path.join("app", "src", "main", "assets", "puzzles", "mate_in_1.csv.gz")
OUTPUT_MATE_IN_2 = os.path.join("app", "src", "main", "assets", "puzzles", "mate_in_2.csv.gz")

TARGET_M1_COUNT = 1200
M1_MIN_RATING = 1100
M1_MAX_RATING = 1600

TARGET_M2_COUNT = 800
M2_MIN_RATING = 1300
M2_MAX_RATING = 1800

FIELDS = ["PuzzleId", "FEN", "Moves", "Rating", "Themes"]

PROMO_MAP = {"q": "q", "r": "r", "b": "b", "n": "n"}
KNIGHT_OFFSETS = ((1, 2), (2, 1), (2, -1), (1, -2), (-1, -2), (-2, -1), (-2, 1), (-1, 2))
KING_OFFSETS = ((1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1))
ORTHO_DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))
DIAG_DIRS = ((1, 1), (1, -1), (-1, 1), (-1, -1))


def sq_index(f, r):
    return r * 8 + f


def parse_uci(uci):
    if len(uci) not in (4, 5):
        return None
    f1 = ord(uci[0]) - 97
    r1 = ord(uci[1]) - 49
    f2 = ord(uci[2]) - 97
    r2 = ord(uci[3]) - 49
    if not (0 <= f1 < 8 and 0 <= r1 < 8 and 0 <= f2 < 8 and 0 <= r2 < 8):
        return None
    promo = uci[4].lower() if len(uci) == 5 else None
    if promo is not None and promo not in PROMO_MAP:
        return None
    return (sq_index(f1, r1), sq_index(f2, r2), promo)


class Board:
    __slots__ = ("squares", "turn", "castling", "ep")

    def __init__(self, squares, turn, castling, ep):
        self.squares = squares
        self.turn = turn  # 'w' or 'b'
        self.castling = castling  # set/string of KQkq
        self.ep = ep  # int index or None

    @staticmethod
    def from_fen(fen):
        parts = fen.strip().split()
        if len(parts) < 4:
            return None
        ranks = parts[0].split("/")
        if len(ranks) != 8:
            return None
        squares = [None] * 64
        for fen_r, rank_str in enumerate(ranks):
            r = 7 - fen_r
            f = 0
            for ch in rank_str:
                if ch.isdigit():
                    f += int(ch)
                elif ch in "prnbqkPRNBQK":
                    if f >= 8:
                        return None
                    squares[sq_index(f, r)] = ch
                    f += 1
                else:
                    return None
            if f != 8:
                return None
        turn = parts[1]
        if turn not in ("w", "b"):
            return None
        castling = "" if parts[2] == "-" else parts[2]
        ep = None if parts[3] == "-" else (
            sq_index(ord(parts[3][0]) - 97, ord(parts[3][1]) - 49)
            if len(parts[3]) == 2 and "a" <= parts[3][0] <= "h" and "1" <= parts[3][1] <= "8"
            else -1
        )
        if ep == -1:
            return None
        return Board(squares, turn, castling, ep)

    def copy(self):
        return Board(self.squares[:], self.turn, self.castling, self.ep)

    def king_square(self, color):
        target = "K" if color == "w" else "k"
        for i, p in enumerate(self.squares):
            if p == target:
                return i
        return None

    def is_attacked_by(self, idx, attacker):
        tf, tr = idx % 8, idx // 8
        sq = self.squares
        # Pawns
        pawn = "P" if attacker == "w" else "p"
        pr = tr - 1 if attacker == "w" else tr + 1
        if 0 <= pr < 8:
            if tf - 1 >= 0 and sq[sq_index(tf - 1, pr)] == pawn:
                return True
            if tf + 1 < 8 and sq[sq_index(tf + 1, pr)] == pawn:
                return True
        # Knights
        knight = "N" if attacker == "w" else "n"
        for df, dr in KNIGHT_OFFSETS:
            nf, nr = tf + df, tr + dr
            if 0 <= nf < 8 and 0 <= nr < 8 and sq[sq_index(nf, nr)] == knight:
                return True
        # Kings
        king = "K" if attacker == "w" else "k"
        for df, dr in KING_OFFSETS:
            kf, kr = tf + df, tr + dr
            if 0 <= kf < 8 and 0 <= kr < 8 and sq[sq_index(kf, kr)] == king:
                return True
        # Ortho sliders
        rook = "R" if attacker == "w" else "r"
        queen = "Q" if attacker == "w" else "q"
        for df, dr in ORTHO_DIRS:
            f, r = tf + df, tr + dr
            while 0 <= f < 8 and 0 <= r < 8:
                p = sq[sq_index(f, r)]
                if p is not None:
                    if p == rook or p == queen:
                        return True
                    break
                f += df
                r += dr
        # Diag sliders
        bishop = "B" if attacker == "w" else "b"
        for df, dr in DIAG_DIRS:
            f, r = tf + df, tr + dr
            while 0 <= f < 8 and 0 <= r < 8:
                p = sq[sq_index(f, r)]
                if p is not None:
                    if p == bishop or p == queen:
                        return True
                    break
                f += df
                r += dr
        return False

    def in_check(self, color):
        ksq = self.king_square(color)
        if ksq is None:
            return False
        opp = "b" if color == "w" else "w"
        return self.is_attacked_by(ksq, opp)

    def apply_move(self, move):
        f_idx, t_idx, promo = move
        nb = self.copy()
        sq = nb.squares
        piece = sq[f_idx]
        is_white = piece.isupper()
        ptype = piece.lower()
        ff, fr = f_idx % 8, f_idx // 8
        tf, tr = t_idx % 8, t_idx // 8

        # En passant capture
        if ptype == "p" and t_idx == self.ep and sq[t_idx] is None:
            cap_idx = sq_index(tf, fr)
            sq[cap_idx] = None

        # Castling rook move
        if ptype == "k" and abs(tf - ff) == 2:
            if tf == 6:
                sq[sq_index(5, fr)] = sq[sq_index(7, fr)]
                sq[sq_index(7, fr)] = None
            elif tf == 2:
                sq[sq_index(3, fr)] = sq[sq_index(0, fr)]
                sq[sq_index(0, fr)] = None

        sq[f_idx] = None
        if promo is not None:
            sq[t_idx] = promo.upper() if is_white else promo.lower()
        else:
            sq[t_idx] = piece

        # Update castling rights
        c = nb.castling
        if c:
            if ptype == "k":
                c = c.replace("K", "").replace("Q", "") if is_white else c.replace("k", "").replace("q", "")
            if f_idx == 0 or t_idx == 0:
                c = c.replace("Q", "")
            if f_idx == 7 or t_idx == 7:
                c = c.replace("K", "")
            if f_idx == 56 or t_idx == 56:
                c = c.replace("q", "")
            if f_idx == 63 or t_idx == 63:
                c = c.replace("k", "")
            nb.castling = c

        # En passant target
        if ptype == "p" and abs(tr - fr) == 2:
            nb.ep = sq_index(ff, (fr + tr) // 2)
        else:
            nb.ep = None

        nb.turn = "b" if self.turn == "w" else "w"
        return nb

    def pseudo_moves(self):
        color = self.turn
        is_w = color == "w"
        sq = self.squares
        moves = []

        for idx, p in enumerate(sq):
            if p is None or (p.isupper() != is_w):
                continue
            f, r = idx % 8, idx // 8
            pt = p.lower()
            if pt == "p":
                step = 1 if is_w else -1
                start_r = 1 if is_w else 6
                promo_r = 7 if is_w else 0
                nr = r + step
                if 0 <= nr < 8:
                    fwd = sq_index(f, nr)
                    if sq[fwd] is None:
                        if nr == promo_r:
                            for pr in ("q", "r", "b", "n"):
                                moves.append((idx, fwd, pr))
                        else:
                            moves.append((idx, fwd, None))
                            if r == start_r:
                                dbl = sq_index(f, r + 2 * step)
                                if sq[dbl] is None:
                                    moves.append((idx, dbl, None))
                    for df in (-1, 1):
                        nf = f + df
                        if 0 <= nf < 8:
                            cap = sq_index(nf, nr)
                            tp = sq[cap]
                            if (tp is not None and tp.isupper() != is_w) or cap == self.ep:
                                if nr == promo_r:
                                    for pr in ("q", "r", "b", "n"):
                                        moves.append((idx, cap, pr))
                                else:
                                    moves.append((idx, cap, None))
            elif pt == "n":
                for df, dr in KNIGHT_OFFSETS:
                    nf, nr = f + df, r + dr
                    if 0 <= nf < 8 and 0 <= nr < 8:
                        ti = sq_index(nf, nr)
                        tp = sq[ti]
                        if tp is None or tp.isupper() != is_w:
                            moves.append((idx, ti, None))
            elif pt in ("b", "r", "q"):
                dirs = DIAG_DIRS if pt == "b" else (ORTHO_DIRS if pt == "r" else ORTHO_DIRS + DIAG_DIRS)
                for df, dr in dirs:
                    nf, nr = f + df, r + dr
                    while 0 <= nf < 8 and 0 <= nr < 8:
                        ti = sq_index(nf, nr)
                        tp = sq[ti]
                        if tp is None:
                            moves.append((idx, ti, None))
                        else:
                            if tp.isupper() != is_w:
                                moves.append((idx, ti, None))
                            break
                        nf += df
                        nr += dr
            elif pt == "k":
                for df, dr in KING_OFFSETS:
                    nf, nr = f + df, r + dr
                    if 0 <= nf < 8 and 0 <= nr < 8:
                        ti = sq_index(nf, nr)
                        tp = sq[ti]
                        if tp is None or tp.isupper() != is_w:
                            moves.append((idx, ti, None))
                # Castling
                opp = "b" if is_w else "w"
                home_r = 0 if is_w else 7
                if r == home_r and f == 4 and not self.is_attacked_by(idx, opp):
                    k_char = "K" if is_w else "k"
                    q_char = "Q" if is_w else "q"
                    if k_char in self.castling:
                        f5, f6 = sq_index(5, home_r), sq_index(6, home_r)
                        if sq[f5] is None and sq[f6] is None and not self.is_attacked_by(f5, opp) and not self.is_attacked_by(f6, opp):
                            moves.append((idx, f6, None))
                    if q_char in self.castling:
                        f3, f2, f1 = sq_index(3, home_r), sq_index(2, home_r), sq_index(1, home_r)
                        if sq[f3] is None and sq[f2] is None and sq[f1] is None and not self.is_attacked_by(f3, opp) and not self.is_attacked_by(f2, opp):
                            moves.append((idx, f2, None))
        return moves

    def legal_moves(self):
        us = self.turn
        legal = []
        for mv in self.pseudo_moves():
            nb = self.apply_move(mv)
            if not nb.in_check(us):
                legal.append(mv)
        return legal

    def is_checkmate(self):
        return self.in_check(self.turn) and len(self.legal_moves()) == 0

    def has_mate_in_one(self):
        for mv in self.legal_moves():
            if self.apply_move(mv).is_checkmate():
                return True
        return False


def verify_mate_in_1(row):
    board = Board.from_fen(row["FEN"])
    if board is None:
        return False
    tokens = row["Moves"].split()
    if len(tokens) != 2:
        return False
    m0 = parse_uci(tokens[0])
    m1 = parse_uci(tokens[1])
    if m0 is None or m1 is None:
        return False
    if m0 not in board.legal_moves():
        return False
    b1 = board.apply_move(m0)
    if m1 not in b1.legal_moves():
        return False
    b2 = b1.apply_move(m1)
    return b2.is_checkmate()


def verify_mate_in_2(row):
    board = Board.from_fen(row["FEN"])
    if board is None:
        return False
    tokens = row["Moves"].split()
    if len(tokens) != 4:
        return False
    parsed = [parse_uci(tok) for tok in tokens]
    if any(m is None for m in parsed):
        return False
    m0, m1, m2, m3 = parsed
    if m0 not in board.legal_moves():
        return False
    b1 = board.apply_move(m0)
    if b1.has_mate_in_one():
        return False
    if m1 not in b1.legal_moves():
        return False
    b2 = b1.apply_move(m1)
    if b2.is_checkmate():
        return False
    replies = b2.legal_moves()
    if not replies or m2 not in replies:
        return False
    # Forced-ish check matching Kotlin PuzzleRepositoryTest (`isForcedMateInTwo`):
    # every legal opponent reply must leave the solver with a mate-in-1.
    if not all(b2.apply_move(rep).has_mate_in_one() for rep in replies):
        return False
    b3 = b2.apply_move(m2)
    if m3 not in b3.legal_moves():
        return False
    b4 = b3.apply_move(m3)
    return b4.is_checkmate()


def open_input(source):
    if source is None or source == "-":
        return sys.stdin
    if source.endswith(".gz"):
        return gzip.open(source, "rt", encoding="utf-8")
    return open(source, "r", encoding="utf-8")


def write_gz(path, rows):
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=FIELDS, lineterminator="\n")
    writer.writeheader()
    for r in rows:
        writer.writerow(r)
    raw_bytes = buf.getvalue().encode("utf-8")
    with gzip.open(path, "wb") as gz_out:
        gz_out.write(raw_bytes)
    return len(raw_bytes), os.path.getsize(path)


def collect_verified(sources, target_theme, min_rating, max_rating, limit, verifier):
    kept = []
    seen_ids = set()
    rejected = 0
    for src in sources:
        with open_input(src) as f:
            reader = csv.DictReader(f)
            for row in reader:
                pid = row.get("PuzzleId", "").strip()
                if not pid or pid in seen_ids:
                    continue
                try:
                    rating = int(row.get("Rating", 0))
                except ValueError:
                    rejected += 1
                    continue
                themes = row.get("Themes", "").strip()
                if target_theme not in themes.split():
                    continue
                if not (min_rating <= rating <= max_rating):
                    continue
                clean_row = {
                    "PuzzleId": pid,
                    "FEN": row.get("FEN", "").strip(),
                    "Moves": row.get("Moves", "").strip(),
                    "Rating": str(rating),
                    "Themes": themes,
                }
                if verifier(clean_row):
                    kept.append(clean_row)
                    seen_ids.add(pid)
                    if len(kept) >= limit:
                        return kept, rejected
                else:
                    rejected += 1
    return kept, rejected


def main():
    os.makedirs(os.path.dirname(OUTPUT_MATE_IN_1), exist_ok=True)
    args = sys.argv[1:]

    if args == ["--verify-bundled"]:
        m1_rows, m1_rejected = collect_verified(
            [OUTPUT_MATE_IN_1], "mateIn1", M1_MIN_RATING, M1_MAX_RATING, TARGET_M1_COUNT, verify_mate_in_1
        )
        m2_rows, m2_rejected = collect_verified(
            [OUTPUT_MATE_IN_2], "mateIn2", M2_MIN_RATING, M2_MAX_RATING, TARGET_M2_COUNT, verify_mate_in_2
        )
    else:
        source = args[0] if args else "-"
        m1_rows, m1_rejected = collect_verified(
            [source], "mateIn1", M1_MIN_RATING, M1_MAX_RATING, TARGET_M1_COUNT, verify_mate_in_1
        )
        m2_rows, m2_rejected = collect_verified(
            [source], "mateIn2", M2_MIN_RATING, M2_MAX_RATING, TARGET_M2_COUNT, verify_mate_in_2
        )

    uncomp_m1, comp_m1 = write_gz(OUTPUT_MATE_IN_1, m1_rows)
    print(
        f"Standard (mateIn1, {M1_MIN_RATING}-{M1_MAX_RATING}): "
        f"verified={len(m1_rows)}, rejected={m1_rejected}, "
        f"bytes={comp_m1} (raw={uncomp_m1})"
    )

    uncomp_m2, comp_m2 = write_gz(OUTPUT_MATE_IN_2, m2_rows)
    print(
        f"Hard (mateIn2, {M2_MIN_RATING}-{M2_MAX_RATING}): "
        f"verified={len(m2_rows)}, rejected={m2_rejected}, "
        f"bytes={comp_m2} (raw={uncomp_m2})"
    )


if __name__ == "__main__":
    main()
