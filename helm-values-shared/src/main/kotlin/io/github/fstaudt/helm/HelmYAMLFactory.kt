package io.github.fstaudt.helm

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_FLOAT
import com.fasterxml.jackson.core.JsonToken.VALUE_STRING
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.core.io.IOContext
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLParser
import org.yaml.snakeyaml.events.ScalarEvent
import java.io.CharArrayReader
import java.io.InputStream
import java.io.Reader

/**
 * YAML factory that reads base-60 scalars (e.g. times like 00:00:00.000) as strings.
 *
 * SnakeYAML resolves such scalars as YAML 1.1 sexagesimal floats, but Jackson cannot convert them to numbers
 * and fails with "Malformed numeric value". Jackson already reads base-60 integers as strings.
 */
class HelmYAMLFactory : YAMLFactory {
    constructor() : super()
    private constructor(src: HelmYAMLFactory, codec: ObjectCodec?) : super(src, codec)

    override fun copy() = HelmYAMLFactory(this, null)

    override fun _createParser(input: InputStream, ctxt: IOContext): YAMLParser =
        Parser(ctxt, _createReader(input, null, ctxt))

    override fun _createParser(reader: Reader, ctxt: IOContext): YAMLParser = Parser(ctxt, reader)

    override fun _createParser(data: CharArray, offset: Int, len: Int, ctxt: IOContext, recyclable: Boolean): YAMLParser =
        Parser(ctxt, CharArrayReader(data, offset, len))

    override fun _createParser(data: ByteArray, offset: Int, len: Int, ctxt: IOContext): YAMLParser =
        Parser(ctxt, _createReader(data, offset, len, null, ctxt))

    private inner class Parser(ctxt: IOContext, reader: Reader) :
        YAMLParser(ctxt, _parserFeatures, _yamlParserFeatures, _loaderOptions, _objectCodec, reader) {
        override fun _decodeScalar(scalar: ScalarEvent): JsonToken? {
            return super._decodeScalar(scalar).takeUnless { it == VALUE_NUMBER_FLOAT && ':' in scalar.value }
                ?: VALUE_STRING
        }
    }
}
