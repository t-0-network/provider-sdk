import type { Path } from "@bufbuild/protobuf/reflect";

/**
 * The field path of a violation as every SDK writes it (field_path_cases in
 * cross_test/test_vectors.json): field names joined by ".", each list index or
 * map key in brackets after its field name, and a string key in double quotes
 * with JSON string escaping. An empty path is "". Oneof and extension items
 * are written as protobuf-es's pathToString writes them.
 */
export function fieldPathString(path: Path): string {
  const out: string[] = [];
  for (const item of path) {
    switch (item.kind) {
      case "field":
      case "oneof":
        if (out.length > 0) {
          out.push(".");
        }
        out.push(item.name);
        break;
      case "extension":
        out.push("[", item.typeName, "]");
        break;
      case "list_sub":
        out.push("[", String(item.index), "]");
        break;
      case "map_sub":
        out.push("[", typeof item.key == "string" ? jsonString(item.key) : String(item.key), "]");
        break;
    }
  }
  return out.join("");
}

const shortEscapes: Record<string, string> = {
  '"': '\\"',
  "\\": "\\\\",
  "\b": "\\b",
  "\f": "\\f",
  "\n": "\\n",
  "\r": "\\r",
  "\t": "\\t",
};

/**
 * `s` in double quotes with JSON string escaping (RFC 8259): \" and \\, \b,
 * \f, \n, \r and \t, every other character below U+0020 as \u00xx, and every
 * other character as it is, DEL and all non-ASCII characters included.
 */
function jsonString(s: string): string {
  const escaped = s.replace(/["\\\u0000-\u001f]/g, (c) =>
    shortEscapes[c] ?? "\\u" + c.charCodeAt(0).toString(16).padStart(4, "0"),
  );
  return `"${escaped}"`;
}
