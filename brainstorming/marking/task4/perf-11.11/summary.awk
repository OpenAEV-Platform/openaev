# Best of the runs per statement: execution time and the top node's buffers.
# Input: results-jit-<on|off>/A<a>-idx<off|on>-<reader>.txt files. Output: TSV.
BEGIN { OFS = "\t"; print "A", "index", "reader", "statement", "best_ms", "top_buffers" }
FNR == 1 {
  split(FILENAME, parts, "/"); name = parts[length(parts)]; sub(/\.txt$/, "", name)
  split(name, key, "-"); a = substr(key[1], 2); idx = substr(key[2], 4); reader = key[3]
}
/^### / { label = $2; wantBuffers = 1; next }
wantBuffers && /Buffers:/ { sub(/^ */, ""); buffers[a, idx, reader, label] = $0; wantBuffers = 0 }
/Execution Time:|^Time:/ {
  ms = ($1 == "Time:") ? $2 : $3
  k = a SUBSEP idx SUBSEP reader SUBSEP label
  if (!(k in best) || ms + 0 < best[k] + 0) best[k] = ms
  if (!(k in seen)) { seen[k] = 1; order[++n] = k }
}
END {
  for (i = 1; i <= n; i++) {
    split(order[i], f, SUBSEP)
    print f[1], f[2], f[3], f[4], best[order[i]], buffers[f[1], f[2], f[3], f[4]]
  }
}
