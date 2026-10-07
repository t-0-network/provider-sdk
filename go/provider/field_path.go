package provider

import (
	"strconv"
	"strings"

	"buf.build/gen/go/bufbuild/protovalidate/protocolbuffers/go/buf/validate"
)

// fieldPathString writes the field path of a violation as every SDK writes it (field_path_cases in
// cross_test/test_vectors.json): field names joined by ".", each list index or map key in brackets
// after its field name, and a string key in double quotes with JSON string escaping. An empty path
// is "".
func fieldPathString(path *validate.FieldPath) string {
	var b strings.Builder
	for i, element := range path.GetElements() {
		if i > 0 {
			b.WriteByte('.')
		}
		b.WriteString(element.GetFieldName())
		switch element.WhichSubscript() {
		case validate.FieldPathElement_Index_case:
			b.WriteString("[" + strconv.FormatUint(element.GetIndex(), 10) + "]")
		case validate.FieldPathElement_BoolKey_case:
			b.WriteString("[" + strconv.FormatBool(element.GetBoolKey()) + "]")
		case validate.FieldPathElement_IntKey_case:
			b.WriteString("[" + strconv.FormatInt(element.GetIntKey(), 10) + "]")
		case validate.FieldPathElement_UintKey_case:
			b.WriteString("[" + strconv.FormatUint(element.GetUintKey(), 10) + "]")
		case validate.FieldPathElement_StringKey_case:
			b.WriteByte('[')
			writeJSONString(&b, element.GetStringKey())
			b.WriteByte(']')
		}
	}
	return b.String()
}

// writeJSONString writes s in double quotes with JSON string escaping (RFC 8259): \" and \\, \b,
// \f, \n, \r and \t, every other byte below 0x20 as \u00xx, and every other byte as it is (DEL and
// all non-ASCII included; a UTF-8 sequence has no byte below 0x80).
func writeJSONString(b *strings.Builder, s string) {
	const hex = "0123456789abcdef"
	b.WriteByte('"')
	for i := 0; i < len(s); i++ {
		switch c := s[i]; c {
		case '"':
			b.WriteString(`\"`)
		case '\\':
			b.WriteString(`\\`)
		case '\b':
			b.WriteString(`\b`)
		case '\f':
			b.WriteString(`\f`)
		case '\n':
			b.WriteString(`\n`)
		case '\r':
			b.WriteString(`\r`)
		case '\t':
			b.WriteString(`\t`)
		default:
			if c < 0x20 {
				b.WriteString(`\u00`)
				b.WriteByte(hex[c>>4])
				b.WriteByte(hex[c&0xf])
			} else {
				b.WriteByte(c)
			}
		}
	}
	b.WriteByte('"')
}
