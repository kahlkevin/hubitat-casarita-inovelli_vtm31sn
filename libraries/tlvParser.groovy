// #ifdef MONOLITH_BUILD
/***************************************************************************************************************************
** MATTER TLV PARSER LIBRARY (symbol prefix: tp)
****************************************************************************************************************************/
// #else
/**
 *  Matter TLV Parser Library (symbol prefix: tp)
 *
 *  SPDX-FileCopyrightText: 2026 Kevin Kahl
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright 2026 Kevin Kahl
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

library (
    base: "driver",
    category: "matter",
    name: "tlvParser",
    description: "Matter TLV Parser Library",
    author: "Kevin Kahl",
    namespace: "@NAMESPACE",
    version: "@VERSION"
)

// Dependencies:
// @AT(INCLIB)(debugAndLogging)
// #endif

// #if 0
//  #define L_EXTRA_SAFETY_IN_HOTPATH
// #endif
// #if 1
//  #define L_FAST_TRACK_U1_AND_U2
// #endif
// #define BF_TYPE List
// #define @BF_CREATE(arg) __tp_bf_create(@arg)
// #define @BF_EOF(bf) __tp_bf_eof(@bf)
// #define @BF_UNSAFEPEEK(bf) @bf[1][@bf[0]]
// #define @BF_SKIP(bf,cb) __tp_bf_skip(@bf, @cb)
// #define @BF_SLURP1(bf,cb) __tp_bf_slurp(@bf, @cb)
// #define @BF_SLURP2(bf,cb,rev) __tp_bf_slurp(@bf, @cb, @rev)
// #define @BF_SLURPINTO2(bf,rgb,cb) __tp_bf_slurpInto(@bf, @rgb, @cb)
// #define @BF_SLURPINTO3(bf,rgb,cb,rev) __tp_bf_slurpInto(@bf, @rgb, @cb, @rev)
// #define @BF_SLURPBYTE(bf) __tp_bf_slurpByte(@bf)
// #define @BF_RENDER(bf) __tp_bf_render(@bf)
// #define @BF_POSFIELD(bf) @bf[0]
// #define @BF_BYTESFIELD(bf) @bf[1]
// #define @BF_BYTEARRAYFROM(arg) hubitat.helper.HexUtils.hexStringToByteArray(@arg)
// #ifdef ENABLE_TESTS_FOR_PARSER
//   #define @BF_RESET0(bf) __tp_bf_reset(@bf)
//   #define @BF_RESET1(bf,arg) __tp_bf_reset(@bf, @arg)
// #endif
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
//  #define @HOTPATH_ASSERT(arg) assert(@arg)
// #else
//  #define @HOTPATH_ASSERT(arg)
// #endif
// #if @defined(ENABLE_TESTS_FOR_PARSER) || @defined(L_EXTRA_SAFETY_IN_HOTPATH)
//  #define @BF_HAS(bf,cb) __tp_bf_has(@bf, @cb)
// #endif
// #define L_ELT_BITS 5
// #define L_TAG_MASK 0b1110_0000
// #define L_ELT_MASK 0b0001_1111
// #define L_TAG_ANONYMOUS 0
// #define L_TAG_CONTEXT 1
// #define L_TAG_COMMON2 2
// #define L_TAG_COMMON4 3
// #define L_TAG_IMPLICIT2 4
// #define L_TAG_IMPLICIT4 5
// #define L_TAG_FULL6 6
// #define L_TAG_FULL8 7
// -------------------------------------------------------------------------------------------------------------------------
// PT PUBLIC API
// --

static def parse_tlv(String tlvOctets) {
    @BF_TYPE bf = @BF_CREATE(tlvOctets)
    byte[] buf8 = new byte[8]
    def val = __tp_parse_tlv(bf, buf8)
    assert(@BF_EOF(bf))
    return val
}
// #ifdef ENABLE_PARSER_NUM_HISTO

static void tp_histo_reset() { __tp_num_histo.clear() }

static Map tp_histo_report() {
    Map histo = [:];
    __tp_num_histo.each {
        String key = "${it < 0 ? 'S' : 'U'}${Math.abs(it)}".toString()
        histo[key] = (histo[key] ?: 0) + 1
    }
    return histo
}
// #endif
// #ifdef ENABLE_TESTS_FOR_PARSER

void tp_runAllTests() {
    int i = 1
    __tp_testByteArrayConversion(i++)
    __tp_testByteFeed(i++)
    __tp_testParseNumber(i++)
    __tp_testParseControl(i++)
    __tp_testParseTLV(i++)
}
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// PT PRIVATE API (byteFeed - bf)
// --

static List __tp_bf_create(String tlvOctets) { [0, hubitat.helper.HexUtils.hexStringToByteArray(tlvOctets)] }
static String __tp_bf_render(List bf) { "[pos: ${bf[0]}, a: ${bf[1].collect { __tp_h2(it) }}]" }
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
static boolean __tp_bf_eof(List bf) { bf[0] == bf[1].length }
// #else
static boolean __tp_bf_eof(List bf) { bf[0] >= bf[1].length }
// #endif
// #if @defined(ENABLE_TESTS_FOR_PARSER) || @defined(L_EXTRA_SAFETY_IN_HOTPATH)
static boolean __tp_bf_has(List bf, int cb) { (bf[1].length - bf[0]) >= cb }
// #endif
static int __tp_bf_skip(List bf, int cb) {
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    @HOTPATH_ASSERT(cb >= 0)
// #endif
    def opos = bf[0]
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    bf[0] = Math.min(bf[0] + cb, bf[1].length)
// #else
    bf[0] = bf[0] + cb
// #endif
    return opos
}
static byte[] __tp_bf_slurp(List bf, int cb, boolean reverse = false) {
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    @HOTPATH_ASSERT(cb > 0)
    @HOTPATH_ASSERT(bf[0] + cb <= bf[1].length)
// #endif
    int oldPos = bf[0]
    bf[0] += cb
    reverse ? bf[1][(oldPos+cb-1)..oldPos] : bf[1][oldPos..<(oldPos+cb)]
}
static byte[] __tp_bf_slurpInto(List bf, byte[] rgb, int cb, boolean reverse = false) {
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    @HOTPATH_ASSERT(rgb.length >= cb)
    @HOTPATH_ASSERT(cb > 0)
    @HOTPATH_ASSERT(bf[0] + cb <= bf[1].length)
// #endif
    int anchor = reverse ? bf[0] + cb - 1 : bf[0]
    for (int i = 0; i < cb; ++i) {
        rgb[i] = (byte) bf[1][reverse ? anchor - i : anchor + i]
    }
    bf[0] += cb
    return rgb
}
static byte __tp_bf_slurpByte(List bf) {
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    @HOTPATH_ASSERT(bf[0] < bf[1].length)
// #endif
    byte r = bf[1][bf[0]]
    ++bf[0] // Increment is separate so we avoid increment beyond allowable range (access on previous line will have failed)
    return r
}
// #ifdef ENABLE_TESTS_FOR_PARSER
static void __tp_bf_reset(List bf, String tlvOctets = null) {
    bf[0] = 0
    if (tlvOctets != null) bf[1] = hubitat.helper.HexUtils.hexStringToByteArray(tlvOctets)
}
// #endif

// -------------------------------------------------------------------------------------------------------------------------
// PT PRIVATE API (TLV parser support functions)
// --

// handles little-endian -> long for up to 8 bytes (signed) or 7 bytes (unsigned)
static long __tp_leToLongN(byte[] b, int off, int len, boolean signed) {
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    @HOTPATH_ASSERT(len >= 1 && len <= 8)
    @HOTPATH_ASSERT(off >= 0 && off + len <= b.length)
    @HOTPATH_ASSERT(len < 8 || signed)   // need to move up to BigInteger here
// #endif
    long v = 0L
    for (int i = 0; i < len; ++i) {
        v |= ((long)(b[off + i] & 0xFF)) << (8 * i)
    }

    if (signed && len < 8) {
        long signBit = 1L << (len * 8 - 1)
        if ((v & signBit) != 0) v -= 1L << (len * 8)
    }

    return v
}

// downsizes v to smaller native type that can safely hold maximum and minimum values
static def __tp_bestNativeNumber(def v, int cb, boolean signed) {
    if (signed) {
        if (cb < 8) return v as Integer
        return v as Long
    }
    if (cb < 4) return v as Integer
    if (cb < 8) return v as Long
    return v
}
// #ifdef ENABLE_PARSER_NUM_HISTO

@groovy.transform.Field static List __tp_num_histo = []
// #endif

static def __tp_parse_number(@BF_TYPE bf, byte[] buf8, int cb, boolean signed) {
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    @HOTPATH_ASSERT(cb in [1, 2, 4, 8])
    @HOTPATH_ASSERT(@BF_HAS(bf, cb))
// #endif
// #ifdef ENABLE_PARSER_NUM_HISTO
    __tp_num_histo.add(signed ? -cb : cb)
// #endif
// #ifdef L_FAST_TRACK_U1_AND_U2
    // During a full device attribute dump, U1 and U2 are the two most requested
    // parse types (2613 and 461 calls respectively). No other type is requested
    // nearly as often (next highest is 71 requests for U4). We fast-track these
    // two types and bypass the overhead of the extra function calls and loops.
    if (cb == 1) {
        byte v = @BF_SLURPBYTE(bf)
        return (signed ? v : (v & 0xFF)) as Integer
    } else if (cb == 2 && !signed) {
        int pos = @BF_SKIP(bf, 2)
        byte[] b = @BF_BYTESFIELD(bf)
        return ((b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8)) as Integer
    }
// #endif
    if (cb < 8 || signed) {
        int pos = @BF_SKIP(bf, cb);
        return __tp_bestNativeNumber(__tp_leToLongN(@BF_BYTESFIELD(bf), pos, cb, signed), cb, signed)
    } else {
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
        // Quick guard against caller passing more than 8 bytes, some of which
        // we wouldn't be set here, but which BigInteger would happily read anyway...
        @HOTPATH_ASSERT(buf8.length == 8)
// #endif
        return new BigInteger(1, @BF_SLURPINTO3(bf, buf8, 8, true))
    }
}

static int __tp_intValue(def v) {
    if (!(v instanceof Integer)) {
        assert(v <= Integer.MAX_VALUE)
        v = v.intValue()
    }
    return v
}

static String __tp_parse_string(@BF_TYPE bf, byte[] buf8, int cb) {
    int len = __tp_intValue(__tp_parse_number(bf, buf8, cb, false))
// #ifdef L_EXTRA_SAFETY_IN_HOTPATH
    @HOTPATH_ASSERT(@BF_HAS(bf, len))
// #endif
    int pos = @BF_SKIP(bf, len)
    return new String(@BF_BYTESFIELD(bf), pos, len, "UTF-8")
}

static byte[] __tp_parse_bytes(@BF_TYPE bf, byte[] buf8, int cb) {
    int len = __tp_intValue(__tp_parse_number(bf, buf8, cb, false))
    return @BF_SLURP1(bf, len)
}

static def __tp_parse_container(@BF_TYPE bf, byte[] buf8, boolean isStruct) {
    def r = isStruct ? [:] : []
    while (@BF_UNSAFEPEEK(bf) != DataType.END_CONTAINER) {
        def val = __tp_parse_tlv(bf, buf8)
// #ifdef ENABLE_PARSER_TYPE_RETURN
        r << val.value
// #else
        r << val
// #endif
    }
    @BF_SKIP(bf, 1) // Skip END_CONTAINER marker
    return r
}

static String __tp_h2(def v) { String.format('0x%1$02X', v) }
static String __tp_h4(def v) { String.format('0x%1$04X', v) }
static String __tp_h8(def v) { String.format('0x%1$08X', v) }
static String __tp_h(def v)  { (v > 0xFFFF) ? __tp_h8(v) : ((v > 0xFF) ? __tp_h4(v) : __tp_h2(v)) }

static def __tp_parse_value(@BF_TYPE bf, byte[] buf8, int valueType) {
    switch (valueType) {
        case DataType.INT8:
            return __tp_parse_number(bf, buf8, 1, true)
        case DataType.INT16:
            return __tp_parse_number(bf, buf8, 2, true)
        case DataType.INT32:
            return __tp_parse_number(bf, buf8, 4, true)
        case DataType.INT64:
            return __tp_parse_number(bf, buf8, 8, true)
        case DataType.UINT8:
            return __tp_parse_number(bf, buf8, 1, false)
        case DataType.UINT16:
            return __tp_parse_number(bf, buf8, 2, false)
        case DataType.UINT32:
            return __tp_parse_number(bf, buf8, 4, false)
        case DataType.UINT64:
            return __tp_parse_number(bf, buf8, 8, false)
        case DataType.BOOLEAN_FALSE:
            return false
        case DataType.BOOLEAN_TRUE:
            return true
        case DataType.FLOAT4:
            return Float.intBitsToFloat(__tp_parse_number(bf, buf8, 4, true))
        case DataType.FLOAT8:
            return Double.longBitsToDouble(__tp_parse_number(bf, buf8, 8, true))
        case DataType.UTF81:
            return __tp_parse_string(bf, buf8, 1)
        case DataType.UTF82:
            return __tp_parse_string(bf, buf8, 2)
        case DataType.UTF84:
            return __tp_parse_string(bf, buf8, 4)
        case DataType.UTF88:
            return __tp_parse_string(bf, buf8, 8)
        case DataType.STRING_OCTET1:
            return __tp_parse_bytes(bf, buf8, 1)
        case DataType.STRING_OCTET2:
            return __tp_parse_bytes(bf, buf8, 2)
        case DataType.STRING_OCTET4:
            return __tp_parse_bytes(bf, buf8, 4)
        case DataType.STRING_OCTET8:
            return __tp_parse_bytes(bf, buf8, 8)
        case DataType.NULL:
            return null
        case DataType.STRUCTURE:
            return __tp_parse_container(bf, buf8, true)
        case DataType.ARRAY:
        case DataType.LIST:
            return __tp_parse_container(bf, buf8, false)
        default:
            throw new Exception("Unknown element type: ${valueType}, byteFeed: ${@BF_RENDER(bf)}")
    }
}

// #ifdef ENABLE_TESTS_FOR_PARSER
static def __tp_parse_tlv(@BF_TYPE bf, byte[] buf8, boolean tagOnly = false) {
// #else
static def __tp_parse_tlv(@BF_TYPE bf, byte[] buf8) {
// #endif
    def tag
    // Process control octet (see Matter Core Specification R1.4 §A.7 Control Octet Encoding and §A.8 Tag Encoding)
    byte control = @BF_SLURPBYTE(bf)
    int tagControl = (control & @L_TAG_MASK) >> @L_ELT_BITS
    switch (tagControl) {
        case @L_TAG_ANONYMOUS:
            break
        case @L_TAG_CONTEXT:
            tag = __tp_parse_number(bf, buf8, 1, false); break
        case @L_TAG_COMMON2:
            tag = 'Matter::' + __tp_h(__tp_parse_number(bf, buf8, 2, false)); break
        case @L_TAG_COMMON4:
            tag = 'Matter::' + __tp_h(__tp_parse_number(bf, buf8, 4, false)); break
        case @L_TAG_IMPLICIT2:
            tag = '::' + __tp_h(__tp_parse_number(bf, buf8, 2, false)); break
        case @L_TAG_IMPLICIT4:
            tag = '::' + __tp_h(__tp_parse_number(bf, buf8, 4, false)); break
        case @L_TAG_FULL6:
        case @L_TAG_FULL8:
            int vendorNum = __tp_parse_number(bf, buf8, 2, false)
            int profileNum = __tp_parse_number(bf, buf8, 2, false)
            def tagNum = __tp_parse_number(bf, buf8, tagControl == @L_TAG_FULL8 ? 4 : 2, false)
            // Format per Matter Core Specification R1.4 §C.2.3. Protocol-Specific Tags
            tag = __tp_h4(vendorNum) + '::' + __tp_h4(profileNum) + ':' + __tp_h(tagNum)
            break
    }
    def type = (control & @L_ELT_MASK)
    // Now we have the tag (if any) and value element type
// #ifdef ENABLE_TESTS_FOR_PARSER
    if (tagOnly) return [tag: tag, type: type]
// #endif
    def v = __tp_parse_value(bf, buf8, type)
    if (tag != null) v = [(tag): v]
// #ifdef ENABLE_PARSER_TYPE_RETURN
    return [type: type, value: v]
// #else
    return v
// #endif
}
// #ifdef ENABLE_TESTS_FOR_PARSER

// -------------------------------------------------------------------------------------------------------------------------
// PT TESTS
// --

boolean fails(Closure c) {
    assert(c)
    try {
        c.call()
        return false
    } catch (AssertionError | Exception e) {
        return true
    }
}

void reportResult(int testNum, boolean pass) {
    String s = "[test #${testNum}] (${dl_currentMethod(1)}) : ${pass ? 'pass' : 'FAIL!'}"
    if (pass) info s
    else error s
}

void __tp_testByteArrayConversion(int testNum) {
    boolean pass
    try {
        assert(@BF_BYTEARRAYFROM('0C05312E312E35') == [0x0C, 0x05, 0x31, 0x2E, 0x31, 0x2E, 0x35])
        assert(@BF_BYTEARRAYFROM('1524000124010118') == [0x15, 0x24, 0x00, 0x01, 0x24, 0x01, 0x01, 0x18])
        assert(@BF_BYTEARRAYFROM('0400') == [0x04, 0x00])
        assert(@BF_BYTEARRAYFROM('040') == [0x00, 0x40])
        pass = true
    } catch (AssertionError | Exception e) {
        error e
    }
    reportResult(testNum, pass)
}

void __tp_testByteFeed(int testNum) {
    boolean pass
    try {
        String val1 = "047D"
        String val2 = "0C05312E312E35"
        @BF_TYPE bfa = @BF_CREATE(val1)
        @BF_TYPE bfb = @BF_CREATE(val2)
        assert(!@BF_EOF(bfa))
        assert(!@BF_EOF(bfb))
        assert(@BF_SLURP1(bfb, 2) == [0x0c, 0x05])
        assert(@BF_SLURP1(bfa, 1) == [0x04])
        assert(@BF_SLURP1(bfb, 2) == [0x31, 0x2e])
        assert(@BF_SLURP1(bfa, 1) == [0x7D])
        assert(@BF_EOF(bfa))
        assert(@BF_SLURP2(bfb, 3, true) == [0x35, 0x2e, 0x31])
        assert(@BF_EOF(bfb))
        @BF_RESET0(bfb)
        assert(@BF_POSFIELD(bfb) == 0)
        assert(!@BF_EOF(bfb))
        byte[] rgb = new byte[4]
        @BF_SLURPINTO2(bfb, rgb, 2)
        assert(rgb[0..1] == [0x0c, 0x05])
        @BF_SLURPINTO3(bfb, rgb, 4, true)
        assert(rgb == [0x2e, 0x31, 0x2e, 0x31])
        @BF_RESET1(bfa, '0C05312E312E35')
        assert(@BF_RENDER(bfa) == "[pos: 0, a: [0x0C, 0x05, 0x31, 0x2E, 0x31, 0x2E, 0x35]]")
        assert(0 == @BF_SKIP(bfa, 6))
        assert(@BF_POSFIELD(bfa) == 6)
        assert(@BF_SLURPBYTE(bfa) == 0x35)
        assert(fails { @BF_SLURPBYTE(bfa) })
        assert(@BF_EOF(bfa))
        @BF_RESET0(bfa)
        assert(@BF_HAS(bfa, 7))
        assert(fails { assert(@BF_HAS(bfa, 8)) })
        assert(0 == @BF_SKIP(bfa, 42))
        assert(fails { assert(@BF_HAS(bfa, 1)) })
        assert(@BF_EOF(bfa))
        @BF_RESET1(bfb, '')
        assert(@BF_EOF(bfb))
        pass = true
    } catch (AssertionError | Exception e) {
        error e
    }
    reportResult(testNum, pass)
}

void __tp_testParseNumber(int testNum) {
    boolean pass
    try {
        @BF_TYPE bf = @BF_CREATE('00')
        byte[] buf8 = new byte[8]
        [
            [hex: '00',               cb: 1, signed: true,  type: Integer,    value: 0],
            [hex: '7F',               cb: 1, signed: true,  type: Integer,    value: 127],
            [hex: '80',               cb: 1, signed: true,  type: Integer,    value: -128],
            [hex: 'FF',               cb: 1, signed: true,  type: Integer,    value: -1],
            [hex: '0000',             cb: 2, signed: true,  type: Integer,    value: 0],
            [hex: 'FF7F',             cb: 2, signed: true,  type: Integer,    value: 32767],
            [hex: '0080',             cb: 2, signed: true,  type: Integer,    value: -32768],
            [hex: 'FFFF',             cb: 2, signed: true,  type: Integer,    value: -1],
            [hex: '00000000',         cb: 4, signed: true,  type: Integer,    value: 0],
            [hex: 'FFFFFF7F',         cb: 4, signed: true,  type: Integer,    value: 2147483647],
            [hex: '00000080',         cb: 4, signed: true,  type: Integer,    value: Integer.MIN_VALUE],
            [hex: 'FFFFFFFF',         cb: 4, signed: true,  type: Integer,    value: -1],
            [hex: '0000000000000000', cb: 8, signed: true,  type: Long,       value: 0L],
            [hex: 'FFFFFFFFFFFFFF7F', cb: 8, signed: true,  type: Long,       value: Long.MAX_VALUE],
            [hex: '0000000000000080', cb: 8, signed: true,  type: Long,       value: Long.MIN_VALUE],
            [hex: 'FFFFFFFFFFFFFFFF', cb: 8, signed: true,  type: Long,       value: -1L],
            [hex: '00',               cb: 1, signed: false, type: Integer,    value: 0],
            [hex: '80',               cb: 1, signed: false, type: Integer,    value: 128],
            [hex: 'FF',               cb: 1, signed: false, type: Integer,    value: 255],
            [hex: '0000',             cb: 2, signed: false, type: Integer,    value: 0],
            [hex: '0080',             cb: 2, signed: false, type: Integer,    value: 32768],
            [hex: 'FFFF',             cb: 2, signed: false, type: Integer,    value: 65535],
            [hex: '00000000',         cb: 4, signed: false, type: Long,       value: 0L],
            [hex: '00000080',         cb: 4, signed: false, type: Long,       value: 2147483648L],
            [hex: 'FFFFFFFF',         cb: 4, signed: false, type: Long,       value: 4294967295L],
            [hex: '0000000000000000', cb: 8, signed: false, type: BigInteger, value: BigInteger.ZERO],
            [hex: '0100000000000000', cb: 8, signed: false, type: BigInteger, value: BigInteger.ONE],
            [hex: '0000000000000080', cb: 8, signed: false, type: BigInteger, value: new BigInteger('9223372036854775808')],
            [hex: 'FFFFFFFFFFFFFFFF', cb: 8, signed: false, type: BigInteger, value: new BigInteger('18446744073709551615')],
        ].each {
            @BF_RESET1(bf, it.hex)
            def value = __tp_parse_number(bf, buf8, it.cb, it.signed)
            assert('class ' + getObjectClassName(value) == it.type.toString())
            assert(value == it.value)
            assert(@BF_EOF(bf))
        }
        pass = true
    } catch (AssertionError | Exception e) {
        error e
    }
    reportResult(testNum, pass)
}

void __tp_testParseControl(int testNum) {
    boolean pass
    try {
        @BF_TYPE bf = @BF_CREATE('04')
        byte[] buf8 = new byte[8]
        List tests = [
            ['04',                 null],
            ['242A',               42],
            ['44FF00',             'Matter::0xFF'],
            ['440001',             'Matter::0x0100'],
            ['64FFFF0000',         'Matter::0xFFFF'],
            ['6400000100',         'Matter::0x00010000'],
            ['84FF00',             '::0xFF'],
            ['840001',             '::0x0100'],
            ['A4FFFF0000',         '::0xFFFF'],
            ['A400000100',         '::0x00010000'],
            ['C4F2FF21433412',     '0xFFF2::0x4321:0x1234'],
            ['E4F2FF214300000100', '0xFFF2::0x4321:0x00010000'],
        ]
        for (int i = 0; i < tests.size(); i++) {
            List test = tests[i]
            @BF_RESET1(bf, test[0])
            Map c = __tp_parse_tlv(bf, buf8, true)
            assert(c.type == DataType.UINT8)
            assert(c.tag == test[1])
        }

        pass = true
    } catch (AssertionError | Exception e) {
        error e
    }
    reportResult(testNum, pass)
}

void __tp_testParseTLV(int testNum) {
    boolean pass
    try {
        @BF_TYPE bf = @BF_CREATE('00')
        byte[] buf8 = new byte[8]
        List tests = [
            [hex: '00FF',                               type: DataType.INT8,          cls: Integer,    value: -1],
            [hex: '01FEFF',                             type: DataType.INT16,         cls: Integer,    value: -2],
            [hex: '02FDFFFFFF',                         type: DataType.INT32,         cls: Integer,    value: -3],
            [hex: '03FCFFFFFFFFFFFFFF',                 type: DataType.INT64,         cls: Long,       value: -4L],
            [hex: '04FE',                               type: DataType.UINT8,         cls: Integer,    value: 254],
            [hex: '057E04',                             type: DataType.UINT16,        cls: Integer,    value: 1150],
            [hex: '0600000080',                         type: DataType.UINT32,        cls: Long,       value: 2147483648L],
            [hex: '07FFFFFFFFFFFFFFFF',                 type: DataType.UINT64,        cls: BigInteger, value: new BigInteger('18446744073709551615')],
            [hex: '08',                                 type: DataType.BOOLEAN_FALSE, cls: Boolean,    value: false],
            [hex: '09',                                 type: DataType.BOOLEAN_TRUE,  cls: Boolean,    value: true],
            [hex: '0A0000803F',                         type: DataType.FLOAT4,        cls: Float,      value: 1.0F],
            [hex: '0B000000000000F03F',                 type: DataType.FLOAT8,        cls: Double,     value: 1.0D],
            [hex: '0C03686579',                         type: DataType.UTF81,         cls: String,     value: 'hey'],
            [hex: '0D0300686579',                       type: DataType.UTF82,         cls: String,     value: 'hey'],
            [hex: '0E03000000686579',                   type: DataType.UTF84,         cls: String,     value: 'hey'],
            [hex: '0F0300000000000000686579',           type: DataType.UTF88,         cls: String,     value: 'hey'],
            [hex: '1003010203',                         type: DataType.STRING_OCTET1, cls: byte[],     value: [1, 2, 3]],
            [hex: '110300010203',                       type: DataType.STRING_OCTET2, cls: byte[],     value: [1, 2, 3]],
            [hex: '1203000000010203',                   type: DataType.STRING_OCTET4, cls: byte[],     value: [1, 2, 3]],
            [hex: '130300000000000000010203',           type: DataType.STRING_OCTET8, cls: byte[],     value: [1, 2, 3]],
            [hex: '14',                                 type: DataType.NULL,                           value: null],
            [hex: '1524000124010218',                   type: DataType.STRUCTURE,     cls: Map,        value: [0: 1, 1: 2]],
            [hex: '160401040218',                       type: DataType.ARRAY,         cls: List,       value: [1, 2]],
            [hex: '1724000124010218',                   type: DataType.LIST,          cls: List,       value: [[0: 1], [1: 2]]],
            [hex: '242A7F',                             type: DataType.UINT8,                          value: [42: 127]], // Code coverage
            [hex: '8442007F',                           type: DataType.UINT8,                          value: ['::0x42': 127]], // Variation check (optional)
            [hex: '152C000656656E646F722C0103496E6F18', type: DataType.STRUCTURE,     cls: Map,        value: [0: 'Vendor', 1: 'Ino']], // Variation check (optional)
        ]
        for (int i = 0; i < tests.size(); i++) {
            Map test = tests[i]
            @BF_RESET1(bf, test.hex)
// #ifdef ENABLE_PARSER_TYPE_RETURN
            Map r = __tp_parse_tlv(bf, buf8)
            assert(r.type == test.type)
// #else
            Map r = [value: __tp_parse_tlv(bf, buf8)]
// #endif
            if (test.cls == byte[]) assert(r.value instanceof byte[])
            else if (test.cls == List) assert(r.value instanceof List)
            else if (test.cls == Map) assert(r.value instanceof Map)
            else if (test.cls != null) assert('class ' + getObjectClassName(r.value) == test.cls.toString())
            if (r.value instanceof byte[]) assert(r.value as List == test.value)
            else assert(r.value == test.value)
            assert(@BF_EOF(bf))
        }

        List oversizedLenTests = [
            '0E00000080',             // UTF84 length = Integer.MAX_VALUE + 1
            '0F0000008000000000',     // UTF88 length = Integer.MAX_VALUE + 1
            '1200000080',             // STRING_OCTET4 length = Integer.MAX_VALUE + 1
            '130000008000000000',     // STRING_OCTET8 length = Integer.MAX_VALUE + 1
        ]
        oversizedLenTests.each {
            @BF_RESET1(bf, it)
            assert(fails { __tp_parse_tlv(bf, buf8) })
        }

        pass = true
    } catch (AssertionError | Exception e) {
        error e
    }
    reportResult(testNum, pass)
}
// #endif
