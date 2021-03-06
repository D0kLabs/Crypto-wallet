package com.d0klabs.cryptowalt.data;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.security.AccessController;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class ObjectName implements Comparable<ObjectName> {
    private static final int DOMAIN_PATTERN = 0x8000_0000;
    private static final int PROPLIST_PATTERN = 0x4000_0000;
    private static final int PROPVAL_PATTERN = 0x2000_0000;

    private static final int FLAG_MASK = DOMAIN_PATTERN | PROPLIST_PATTERN |
            PROPVAL_PATTERN;
    private static final int DOMAIN_LENGTH_MASK = ~FLAG_MASK;
    //TODO: revrite Object params with all "if"


    private static class Property {

        int _key_index;
        int _key_length;
        int _value_length;

        /**
         * Constructor.
         */
        Property(int key_index, int key_length, int value_length) {
            _key_index = key_index;
            _key_length = key_length;
            _value_length = value_length;
        }

        /**
         * Assigns the key index of property
         */
        void setKeyIndex(int key_index) {
            _key_index = key_index;
        }

        /**
         * Returns a key string for receiver key
         */
        String getKeyString(String name) {
            return name.substring(_key_index, _key_index + _key_length);
        }

        /**
         * Returns a value string for receiver key
         */
        String getValueString(String name) {
            int in_begin = _key_index + _key_length + 1;
            int out_end = in_begin + _value_length;
            return name.substring(in_begin, out_end);
        }
    }


    private static class PatternProperty extends Property {

        PatternProperty(int key_index, int key_length, int value_length) {
            super(key_index, key_length, value_length);
        }
    }


    private static final long oldSerialVersionUID = -5467795090068647408L;
    // Serial version for new serial form
    private static final long newSerialVersionUID = 1081892073854801359L;
    // Serializable fields in old serial form
    private static final ObjectStreamField[] oldSerialPersistentFields =
            {
                    new ObjectStreamField("domain", String.class),
                    new ObjectStreamField("propertyList", Hashtable.class),
                    new ObjectStreamField("propertyListString", String.class),
                    new ObjectStreamField("canonicalName", String.class),
                    new ObjectStreamField("pattern", Boolean.TYPE),
                    new ObjectStreamField("propertyPattern", Boolean.TYPE)
            };
    //
    // Serializable fields in new serial form
    private static final ObjectStreamField[] newSerialPersistentFields = { };
    //
    // Actual serial version and serial form
    private static final long serialVersionUID;
    private static final ObjectStreamField[] serialPersistentFields;
    private static boolean compat = false;
    static {
        try {
            GetPropertyAction act = new GetPropertyAction("jmx.serial.form");
            String form = AccessController.doPrivileged(act);
            compat = (form != null && form.equals("1.0"));
        } catch (Exception e) {
            // OK: exception means no compat with 1.0, too bad
        }
        if (compat) {
            serialPersistentFields = oldSerialPersistentFields;
            serialVersionUID = oldSerialVersionUID;
        } else {
            serialPersistentFields = newSerialPersistentFields;
            serialVersionUID = newSerialVersionUID;
        }
    }

    static final private Property[] _Empty_property_array = new Property[0];



    private transient String _canonicalName;



    private transient Property[] _kp_array;


    private transient Property[] _ca_array;



    private transient Map<String,String> _propertyList;


    private transient int _compressed_storage = 0x0;

    private void construct(String name){

        // The name cannot be null
        if (name == null)
            throw new NullPointerException("name cannot be null");

        // Test if the name is empty
        if (name.length() == 0) {
            // this is equivalent to the whole word query object name.
            _canonicalName = "*:*";
            _kp_array = _Empty_property_array;
            _ca_array = _Empty_property_array;
            setDomainLength(1);
            _propertyList = null;
            setDomainPattern(true);
            setPropertyListPattern(true);
            setPropertyValuePattern(false);
            return;
        }

        // initialize parsing of the string
        final char[] name_chars = name.toCharArray();
        final int len = name_chars.length;
        final char[] canonical_chars = new char[len]; // canonical form will
        // be same length at most
        int cname_index = 0;
        int index = 0;
        char c, c1;

        // parses domain part
        domain_parsing:
        while (index < len) {
            switch (name_chars[index]) {
                case ':' :
                    setDomainLength(index++);
                    break domain_parsing;
                case '=' :
                    // ":" omission check.
                    //
                    // Although "=" is a valid character in the domain part
                    // it is true that it is rarely used in the real world.
                    // So check straight away if the ":" has been omitted
                    // from the ObjectName. This allows us to provide a more
                    // accurate exception message.
                    int i = ++index;
                    while ((i < len) && (name_chars[i++] != ':'))
                        if (i == len){}
                            //throw new MalformedObjectNameException(
                            //        "Domain part must be specified");
                    break;
                case '\n' :
                case '*' :
                case '?' :
                    setDomainPattern(true);
                    index++;
                    break;
                default :
                    index++;
                    break;
            }
        }

        // check for non-empty properties
        if (index == len){}
            //throw new MalformedObjectNameException(
            //        "Key properties cannot be empty");

        // we have got the domain part, begins building of _canonicalName
        int _domain_length = getDomainLength();
        System.arraycopy(name_chars, 0, canonical_chars, 0, _domain_length);
        canonical_chars[_domain_length] = ':';
        cname_index = _domain_length + 1;

        // parses property list
        Property prop;
        Map<String,Property> keys_map = new HashMap<String,Property>();
        String[] keys;
        String key_name;
        boolean quoted_value;
        int property_index = 0;
        int in_index;
        int key_index, key_length, value_index, value_length = 0;

        keys = new String[10];
        _kp_array = new Property[10];
        setPropertyListPattern(false);
        setPropertyValuePattern(false);

        while (index < len) {
            c = name_chars[index];

            // case of pattern properties
            if (c == '*') {
                if (isPropertyListPattern()){}
                    //throw new MalformedObjectNameException(
                    //        "Cannot have several '*' characters in pattern " +
                    //                "property list");
                else {
                    setPropertyListPattern(true);
                    if ((++index < len ) && (name_chars[index] != ',')){}
                        //throw new MalformedObjectNameException(
                        //        "Invalid character found after '*': end of " +
                         //               "name or ',' expected");
                    else if (index == len) {
                        if (property_index == 0) {
                            // empty properties case
                            _kp_array = _Empty_property_array;
                            _ca_array = _Empty_property_array;
                            _propertyList = Collections.emptyMap();
                        }
                        break;
                    } else {
                        // correct pattern spec in props, continue
                        index++;
                        continue;
                    }
                }
            }

            // standard property case, key part
            in_index = index;
            key_index = in_index;
            if (name_chars[in_index] == '='){}
                //throw new MalformedObjectNameException("Invalid key (empty)");
            while ((in_index < len) && ((c1 = name_chars[in_index++]) != '='))
                switch (c1) {
                    // '=' considered to introduce value part
                    case  '*' :
                    case  '?' :
                    case  ',' :
                    case  ':' :
                    case  '\n' :
                        final String ichar = ((c1=='\n')?"\\n":""+c1);
                        //throw new MalformedObjectNameException(
                        //        "Invalid character '" + ichar +
                        //                "' in key part of property");
                }
            if (name_chars[in_index - 1] != '='){}
                //throw new MalformedObjectNameException(
                //        "Unterminated key property part");
            value_index = in_index; // in_index pointing after '=' char
            key_length = value_index - key_index - 1; // found end of key

            // standard property case, value part
            boolean value_pattern = false;
            if (in_index < len && name_chars[in_index] == '\"') {
                quoted_value = true;
                // the case of quoted value part
                quoted_value_parsing:
                while ((++in_index < len) &&
                        ((c1 = name_chars[in_index]) != '\"')) {
                    // the case of an escaped character
                    if (c1 == '\\') {
                        if (++in_index == len){}
                        //    throw new MalformedObjectNameException(
                        //            "Unterminated quoted value");
                        switch (c1 = name_chars[in_index]) {
                            case '\\' :
                            case '\"' :
                            case '?' :
                            case '*' :
                            case 'n' :
                                break; // valid character
                            default :
                                //throw new MalformedObjectNameException(
                                //        "Invalid escape sequence '\\" +
                                //                c1 + "' in quoted value");
                        }
                    } else if (c1 == '\n') {
                        //throw new MalformedObjectNameException(
                        //        "Newline in quoted value");
                    } else {
                        switch (c1) {
                            case '?' :
                            case '*' :
                                value_pattern = true;
                                break;
                        }
                    }
                }
                if (in_index == len){}
                    //throw new MalformedObjectNameException(
                    //        "Unterminated quoted value");
                else value_length = ++in_index - value_index;
            } else {
                // the case of standard value part
                quoted_value = false;
                while ((in_index < len) && ((c1 = name_chars[in_index]) != ','))
                    switch (c1) {
                        // ',' considered to be the value separator
                        case '*' :
                        case '?' :
                            value_pattern = true;
                            in_index++;
                            break;
                        case '=' :
                        case ':' :
                        case '"' :
                        case '\n' :
                            final String ichar = ((c1=='\n')?"\\n":""+c1);
                            //throw new MalformedObjectNameException(
                            //        "Invalid character '" + ichar +
                            //                "' in value part of property");
                        default :
                            in_index++;
                            break;
                    }
                value_length = in_index - value_index;
            }

            // Parsed property, checks the end of name
            if (in_index == len - 1) {
                if (quoted_value){}
                   // throw new MalformedObjectNameException(
                   //         "Invalid ending character `" +
                   //                 name_chars[in_index] + "'");
                else{} //throw new MalformedObjectNameException(
                     //   "Invalid ending comma");
            } else in_index++;

            // we got the key and value part, prepare a property for this
            if (!value_pattern) {
                prop = new Property(key_index, key_length, value_length);
            } else {
                setPropertyValuePattern(true);
                prop = new PatternProperty(key_index, key_length, value_length);
            }
            key_name = name.substring(key_index, key_index + key_length);

            if (property_index == keys.length) {
                String[] tmp_string_array = new String[property_index + 10];
                System.arraycopy(keys, 0, tmp_string_array, 0, property_index);
                keys = tmp_string_array;
            }
            keys[property_index] = key_name;

            addProperty(prop, property_index, keys_map, key_name);
            property_index++;
            index = in_index;
        }

        // computes and set canonical name
        setCanonicalName(name_chars, canonical_chars, keys,
                keys_map, cname_index, property_index);
    }


    private void construct(String domain, Map<String,String> props){
            //throws MalformedObjectNameException {

        // The domain cannot be null
        if (domain == null)
            throw new NullPointerException("domain cannot be null");

        // The key property list cannot be null
        if (props == null)
            throw new NullPointerException("key property list cannot be null");

        // The key property list cannot be empty
        if (props.isEmpty()){}
            //throw new MalformedObjectNameException(
            //        "key property list cannot be empty");

        // checks domain validity
        if (!isDomain(domain)){}
            //throw new MalformedObjectNameException("Invalid domain: " + domain);

        // init canonicalname
        final StringBuilder sb = new StringBuilder();
        sb.append(domain).append(':');
        setDomainLength(domain.length());

        // allocates the property array
        int nb_props = props.size();
        _kp_array = new Property[nb_props];

        String[] keys = new String[nb_props];
        final Map<String,Property> keys_map = new HashMap<String,Property>();
        Property prop;
        int key_index;
        int i = 0;
        for (Map.Entry<String,String> entry : props.entrySet()) {
            if (sb.length() > 0)
                sb.append(",");
            String key = entry.getKey();
            String value = null;
            try {
                value = entry.getValue();
            } catch (ClassCastException e) {
                //throw new MalformedObjectNameException(e.getMessage());
            }
            key_index = sb.length();
            checkKey(key);
            sb.append(key);
            keys[i] = key;
            sb.append("=");
            boolean value_pattern = checkValue(value);
            sb.append(value);
            if (!value_pattern) {
                prop = new Property(key_index,
                        key.length(),
                        value.length());
            } else {
                setPropertyValuePattern(true);
                prop = new PatternProperty(key_index,
                        key.length(),
                        value.length());
            }
            addProperty(prop, i, keys_map, key);
            i++;
        }

        // initialize canonical name and data structure
        int len = sb.length();
        char[] initial_chars = new char[len];
        sb.getChars(0, len, initial_chars, 0);
        char[] canonical_chars = new char[len];
        int copyLen = getDomainLength() + 1;
        System.arraycopy(initial_chars, 0, canonical_chars, 0, copyLen);
        setCanonicalName(initial_chars, canonical_chars, keys, keys_map,
                copyLen, _kp_array.length);
    }
    /**
     * Add passed property to the list at the given index
     * for the passed key name
     */
    private void addProperty(Property prop, int index,
                             Map<String,Property> keys_map, String key_name){
           // throws MalformedObjectNameException {

        if (keys_map.containsKey(key_name)) {}
            //throw new MalformedObjectNameException("key `" +
            //    key_name +"' already defined");

        // if no more space for property arrays, have to increase it
        if (index == _kp_array.length) {
            Property[] tmp_prop_array = new Property[index + 10];
            System.arraycopy(_kp_array, 0, tmp_prop_array, 0, index);
            _kp_array = tmp_prop_array;
        }
        _kp_array[index] = prop;
        keys_map.put(key_name, prop);
    }

    /**
     * Sets the canonical name of receiver from input 'specified_chars'
     * array, by filling 'canonical_chars' array with found 'nb-props'
     * properties starting at position 'prop_index'.
     */
    private void setCanonicalName(char[] specified_chars,
                                  char[] canonical_chars,
                                  String[] keys, Map<String,Property> keys_map,
                                  int prop_index, int nb_props) {

        // Sort the list of found properties
        if (_kp_array != _Empty_property_array) {
            String[] tmp_keys = new String[nb_props];
            Property[] tmp_props = new Property[nb_props];

            System.arraycopy(keys, 0, tmp_keys, 0, nb_props);
            Arrays.sort(tmp_keys);
            keys = tmp_keys;
            System.arraycopy(_kp_array, 0, tmp_props, 0 , nb_props);
            _kp_array = tmp_props;
            _ca_array = new Property[nb_props];

            // now assigns _ca_array to the sorted list of keys
            // (there cannot be two identical keys in an objectname.
            for (int i = 0; i < nb_props; i++)
                _ca_array[i] = keys_map.get(keys[i]);

            // now we build the canonical name and set begin indexes of
            // properties to reflect canonical form
            int last_index = nb_props - 1;
            int prop_len;
            Property prop;
            for (int i = 0; i <= last_index; i++) {
                prop = _ca_array[i];
                // length of prop including '=' char
                prop_len = prop._key_length + prop._value_length + 1;
                System.arraycopy(specified_chars, prop._key_index,
                        canonical_chars, prop_index, prop_len);
                prop.setKeyIndex(prop_index);
                prop_index += prop_len;
                if (i != last_index) {
                    canonical_chars[prop_index] = ',';
                    prop_index++;
                }
            }
        }

        // terminate canonicalname with '*' in case of pattern
        if (isPropertyListPattern()) {
            if (_kp_array != _Empty_property_array)
                canonical_chars[prop_index++] = ',';
            canonical_chars[prop_index++] = '*';
        }

        // we now build the canonicalname string
        _canonicalName = (new String(canonical_chars, 0, prop_index)).intern();
    }

    /**
     * Parse a key.
     * <pre>final int endKey=parseKey(s,startKey);</pre>
     * <p>key starts at startKey (included), and ends at endKey (excluded).
     * If (startKey == endKey), then the key is empty.
     *
     * @param s The char array of the original string.
     * @param startKey index at which to begin parsing.
     * @return The index following the last character of the key.
     **/
    private static int parseKey(final char[] s, final int startKey){
            //throws MalformedObjectNameException {
        int next   = startKey;
        int endKey = startKey;
        final int len = s.length;
        while (next < len) {
            final char k = s[next++];
            switch (k) {
                case '*':
                case '?':
                case ',':
                case ':':
                case '\n':
                    final String ichar = ((k=='\n')?"\\n":""+k);
                    //throw new MalformedObjectNameException("Invalid character in key: `" + ichar + "'");
                case '=':
                    // we got the key.
                    endKey = next-1;
                    break;
                default:
                    if (next < len) continue;
                    else endKey=next;
            }
            break;
        }
        return endKey;
    }

    /**
     * Parse a value.
     * <pre>final int endVal=parseValue(s,startVal);</pre>
     * <p>value starts at startVal (included), and ends at endVal (excluded).
     * If (startVal == endVal), then the key is empty.
     *
     * @param s The char array of the original string.
     * @param startValue index at which to begin parsing.
     * @return The first element of the int array indicates the index
     *         following the last character of the value. The second
     *         element of the int array indicates that the value is
     *         a pattern when its value equals 1.
     **/
    private static int[] parseValue(final char[] s, final int startValue){
            //throws MalformedObjectNameException {

        boolean value_pattern = false;

        int next   = startValue;
        int endValue = startValue;

        final int len = s.length;
        final char q=s[startValue];

        if (q == '"') {
            // quoted value
            if (++next == len){}
            //throw new MalformedObjectNameException("Invalid quote");
            while (next < len) {
                char last = s[next];
                if (last == '\\') {
                    if (++next == len){}
                    //throw new MalformedObjectNameException("Invalid unterminated quoted character sequence");
                    last = s[next];
                    switch (last) {
                        case '\\' :
                        case '?' :
                        case '*' :
                        case 'n' :
                            break;
                        case '\"' :
                            // We have an escaped quote. If this escaped
                            // quote is the last character, it does not
                            // qualify as a valid termination quote.
                            //
                            if (next+1 == len){}
                                //throw new MalformedObjectNameException( "Missing termination quote");
                            break;
                        default:
                            //throw new MalformedObjectNameException( "Invalid quoted character sequence '\\" + last + "'");
                    }
                } else if (last == '\n') {
                    //throw new MalformedObjectNameException( "Newline in quoted value");
                } else if (last == '\"') {
                    next++;
                    break;
                } else {
                    switch (last) {
                        case '?' :
                        case '*' :
                            value_pattern = true;
                            break;
                    }
                }
                next++;

                // Check that last character is a termination quote.
                // We have already handled the case were the last
                // character is an escaped quote earlier.
                //
                if ((next >= len) && (last != '\"')) {}
                //throw new MalformedObjectNameException("Missing termination quote");
            }
            endValue = next;
            if (next < len) {
                if (s[next++] != ','){}
                //throw new MalformedObjectNameException("Invalid quote");
            }
        } else {
            // Non quoted value.
            while (next < len) {
                final char v=s[next++];
                switch(v) {
                    case '*':
                    case '?':
                        value_pattern = true;
                        if (next < len) continue;
                        else endValue=next;
                        break;
                    case '=':
                    case ':':
                    case '\n' :
                        final String ichar = ((v=='\n')?"\\n":""+v);
                        //throw new MalformedObjectNameException("Invalid character `" + ichar + "' in value");
                    case ',':
                        endValue = next-1;
                        break;
                    default:
                        if (next < len) continue;
                        else endValue=next;
                }
                break;
            }
        }
        return new int[] { endValue, value_pattern ? 1 : 0 };
    }

    /**
     * Check if the supplied value is a valid value.
     *
     * @return true if the value is a pattern, otherwise false.
     */
    private static boolean checkValue(String val){
           // throws MalformedObjectNameException {

        if (val == null) throw new
                NullPointerException("Invalid value (null)");

        final int len = val.length();
        if (len == 0)
            return false;

        final char[] s = val.toCharArray();
        final int[] result = parseValue(s,0);
        final int endValue = result[0];
        final boolean value_pattern = result[1] == 1;
        if (endValue < len) {}
        //throw new MalformedObjectNameException("Invalid character in value: `" + s[endValue] + "'");
        return value_pattern;
    }

    /**
     * Check if the supplied key is a valid key.
     */
    private static void checkKey(String key){
           // throws MalformedObjectNameException {

        if (key == null) throw new
                NullPointerException("Invalid key (null)");

        final int len = key.length();
        if (len == 0){}
        //throw new MalformedObjectNameException("Invalid key (empty)");
        final char[] k=key.toCharArray();
        final int endKey = parseKey(k,0);
        if (endKey < len) {}
        //throw new MalformedObjectNameException("Invalid character in value: `" + k[endKey] + "'");
    }


    // Category : Internal utilities <==============================

    // Category : Internal accessors ------------------------------>

    /**
     * Check if domain is a valid domain.  Set _domain_pattern if appropriate.
     */
    private boolean isDomain(String domain) {
        if (domain == null) return true;
        final int len = domain.length();
        int next = 0;
        while (next < len) {
            final char c = domain.charAt(next++);
            switch (c) {
                case ':' :
                case '\n' :
                    return false;
                case '*' :
                case '?' :
                    setDomainPattern(true);
                    break;
            }
        }
        return true;
    }

    private int getDomainLength() {
        return _compressed_storage & DOMAIN_LENGTH_MASK;
    }

    private void setDomainLength(int length){
        //throws MalformedObjectNameException {
        if ((length & FLAG_MASK) != 0 ) {
            //throw new MalformedObjectNameException("Domain name too long. Maximum allowed domain name length is:"DOMAIN_LENGTH_MASK);
        }
        _compressed_storage = (_compressed_storage & FLAG_MASK) | length;
    }
    /**
     * Deserializes an {@link ObjectName} from an {@link ObjectInputStream}.
     * @serialData <ul>
     *               <li>In the current serial form (value of property
     *                   <code>jmx.serial.form</code> differs from
     *                   <code>1.0</code>): the string
     *                   &quot;&lt;domain&gt;:&lt;properties&gt;&lt;wild&gt;&quot;,
     *                   where: <ul>
     *                            <li>&lt;domain&gt; represents the domain part
     *                                of the {@link ObjectName}</li>
     *                            <li>&lt;properties&gt; represents the list of
     *                                properties, as returned by
     *                                {@link #getKeyPropertyListString}
     *                            <li>&lt;wild&gt; is empty if not
     *                                <code>isPropertyPattern</code>, or
     *                                is the character "<code>*</code>" if
     *                                <code>isPropertyPattern</code>
     *                                and &lt;properties&gt; is empty, or
     *                                is "<code>,*</code>" if
     *                                <code>isPropertyPattern</code> and
     *                                &lt;properties&gt; is not empty.
     *                            </li>
     *                          </ul>
     *                   The intent is that this string could be supplied
     *                   to the {@link #ObjectName(String)} constructor to
     *                   produce an equivalent {@link ObjectName}.
     *               </li>
     *               <li>In the old serial form (value of property
     *                   <code>jmx.serial.form</code> is
     *                   <code>1.0</code>): &lt;domain&gt; &lt;propertyList&gt;
     *                   &lt;propertyListString&gt; &lt;canonicalName&gt;
     *                   &lt;pattern&gt; &lt;propertyPattern&gt;,
     *                   where: <ul>
     *                            <li>&lt;domain&gt; represents the domain part
     *                                of the {@link ObjectName}</li>
     *                            <li>&lt;propertyList&gt; is the
     *                                {@link Hashtable} that contains all the
     *                                pairs (key,value) for this
     *                                {@link ObjectName}</li>
     *                            <li>&lt;propertyListString&gt; is the
     *                                {@link String} representation of the
     *                                list of properties in any order (not
     *                                mandatorily a canonical representation)
     *                                </li>
     *                            <li>&lt;canonicalName&gt; is the
     *                                {@link String} containing this
     *                                {@link ObjectName}'s canonical name</li>
     *                            <li>&lt;pattern&gt; is a boolean which is
     *                                <code>true</code> if this
     *                                {@link ObjectName} contains a pattern</li>
     *                            <li>&lt;propertyPattern&gt; is a boolean which
     *                                is <code>true</code> if this
     *                                {@link ObjectName} contains a pattern in
     *                                the list of properties</li>
     *                          </ul>
     *               </li>
     *             </ul>
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {

        String cn;
        if (compat) {
            // Read an object serialized in the old serial form
            //
            //in.defaultReadObject();
            final ObjectInputStream.GetField fields = in.readFields();
            String propListString =
                    (String)fields.get("propertyListString", "");

            // 6616825: take care of property patterns
            final boolean propPattern =
                    fields.get("propertyPattern" , false);
            if (propPattern) {
                propListString =
                        (propListString.length()==0?"*":(propListString+",*"));
            }

            cn = (String)fields.get("domain", "default")+
                    ":"+ propListString;
        } else {
            // Read an object serialized in the new serial form
            //
            in.defaultReadObject();
            cn = (String)in.readObject();
        }

        try {
            construct(cn);
        } catch (NullPointerException e) {
            throw new InvalidObjectException(e.toString());
        }
    }


    /**
     * Serializes an {@link ObjectName} to an {@link ObjectOutputStream}.
     * @serialData <ul>
     *               <li>In the current serial form (value of property
     *                   <code>jmx.serial.form</code> differs from
     *                   <code>1.0</code>): the string
     *                   &quot;&lt;domain&gt;:&lt;properties&gt;&lt;wild&gt;&quot;,
     *                   where: <ul>
     *                            <li>&lt;domain&gt; represents the domain part
     *                                of the {@link ObjectName}</li>
     *                            <li>&lt;properties&gt; represents the list of
     *                                properties, as returned by
     *                                {@link #getKeyPropertyListString}
     *                            <li>&lt;wild&gt; is empty if not
     *                                <code>isPropertyPattern</code>, or
     *                                is the character "<code>*</code>" if
     *                                this <code>isPropertyPattern</code>
     *                                and &lt;properties&gt; is empty, or
     *                                is "<code>,*</code>" if
     *                                <code>isPropertyPattern</code> and
     *                                &lt;properties&gt; is not empty.
     *                            </li>
     *                          </ul>
     *                   The intent is that this string could be supplied
     *                   to the {@link #ObjectName(String)} constructor to
     *                   produce an equivalent {@link ObjectName}.
     *               </li>
     *               <li>In the old serial form (value of property
     *                   <code>jmx.serial.form</code> is
     *                   <code>1.0</code>): &lt;domain&gt; &lt;propertyList&gt;
     *                   &lt;propertyListString&gt; &lt;canonicalName&gt;
     *                   &lt;pattern&gt; &lt;propertyPattern&gt;,
     *                   where: <ul>
     *                            <li>&lt;domain&gt; represents the domain part
     *                                of the {@link ObjectName}</li>
     *                            <li>&lt;propertyList&gt; is the
     *                                {@link Hashtable} that contains all the
     *                                pairs (key,value) for this
     *                                {@link ObjectName}</li>
     *                            <li>&lt;propertyListString&gt; is the
     *                                {@link String} representation of the
     *                                list of properties in any order (not
     *                                mandatorily a canonical representation)
     *                                </li>
     *                            <li>&lt;canonicalName&gt; is the
     *                                {@link String} containing this
     *                                {@link ObjectName}'s canonical name</li>
     *                            <li>&lt;pattern&gt; is a boolean which is
     *                                <code>true</code> if this
     *                                {@link ObjectName} contains a pattern</li>
     *                            <li>&lt;propertyPattern&gt; is a boolean which
     *                                is <code>true</code> if this
     *                                {@link ObjectName} contains a pattern in
     *                                the list of properties</li>
     *                          </ul>
     *               </li>
     *             </ul>
     */
    private void writeObject(ObjectOutputStream out)
            throws IOException {

        if (compat)
        {
            // Serializes this instance in the old serial form
            // Read CR 6441274 before making any changes to this code
            ObjectOutputStream.PutField fields = out.putFields();
            fields.put("domain", _canonicalName.substring(0, getDomainLength()));
            fields.put("propertyList", getKeyPropertyList());
            fields.put("propertyListString", getKeyPropertyListString());
            fields.put("canonicalName", _canonicalName);
            fields.put("pattern", (_compressed_storage & (DOMAIN_PATTERN | PROPLIST_PATTERN)) != 0);
            fields.put("propertyPattern", isPropertyListPattern());
            out.writeFields();
        }
        else
        {
            // Serializes this instance in the new serial form
            //
            out.defaultWriteObject();
            out.writeObject(getSerializedNameString());
        }
    }

    public static ObjectName getInstance(String name){
           // throws MalformedObjectNameException, NullPointerException {
        return new ObjectName(name);
    }


    public static ObjectName getInstance(String domain, String key,
                                         String value){
            //throws MalformedObjectNameException {
        return new ObjectName(domain, key, value);
    }


    public static ObjectName getInstance(String domain,
                                         Hashtable<String,String> table){
            //throws MalformedObjectNameException {
        return new ObjectName(domain, table);
    }


    public static ObjectName getInstance(ObjectName name) {
        if (name.getClass().equals(ObjectName.class)) {
            return name;
        }
        return newObjectName(name.getSerializedNameString());
    }

    private static ObjectName newObjectName(String serializedNameString) {
        return null;
    }


    public ObjectName(String name){
           // throws MalformedObjectNameException {
        construct(name);
    }


    public ObjectName(String domain, String key, String value){
           // throws MalformedObjectNameException {
        // If key or value are null a NullPointerException
        // will be thrown by the put method in Hashtable.
        //
        Map<String,String> table = Collections.singletonMap(key, value);
        construct(domain, table);
    }


    public ObjectName(String domain, Hashtable<String,String> table){
            //throws MalformedObjectNameException {
        construct(domain, table);
        /* The exception for when a key or value in the table is not a
           String is now ClassCastException rather than
           MalformedObjectNameException.  This was not previously
           specified.  */
    }

    // Category : ObjectName Construction <==============================


    // Category : Getter methods ------------------------------>

    /**
     * Checks whether the object name is a pattern.
     * <p>
     * An object name is a pattern if its domain contains a
     * wildcard or if the object name is a property pattern.
     *
     * @return  True if the name is a pattern, otherwise false.
     */
    public boolean isPattern() {
        return (_compressed_storage & FLAG_MASK) != 0;
    }

    /**
     * Checks whether the object name is a pattern on the domain part.
     *
     * @return  True if the name is a domain pattern, otherwise false.
     *
     */
    public boolean isDomainPattern() {
        return (_compressed_storage & DOMAIN_PATTERN) != 0;
    }

    /**
     * Marks the object name as representing a pattern on the domain part.
     * @param value {@code true} if the domain name is a pattern,
     *              {@code false} otherwise
     */
    private void setDomainPattern(boolean value) {
        if (value) {
            _compressed_storage |= DOMAIN_PATTERN;
        } else {
            _compressed_storage &= ~DOMAIN_PATTERN;
        }
    }

    /**
     * Checks whether the object name is a pattern on the key properties.
     * <p>
     * An object name is a pattern on the key properties if it is a
     * pattern on the key property list (e.g. "d:k=v,*") or on the
     * property values (e.g. "d:k=*") or on both (e.g. "d:k=*,*").
     *
     * @return  True if the name is a property pattern, otherwise false.
     */
    public boolean isPropertyPattern() {
        return (_compressed_storage & (PROPVAL_PATTERN | PROPLIST_PATTERN)) != 0;
    }

    /**
     * Checks whether the object name is a pattern on the key property list.
     * <p>
     * For example, "d:k=v,*" and "d:k=*,*" are key property list patterns
     * whereas "d:k=*" is not.
     *
     * @return  True if the name is a property list pattern, otherwise false.
     *
     * @since 1.6
     */
    public boolean isPropertyListPattern() {
        return (_compressed_storage & PROPLIST_PATTERN) != 0;
    }

    /**
     * Marks the object name as representing a pattern on the key property list.
     * @param value {@code true} if the key property list is a pattern,
     *              {@code false} otherwise
     */
    private void setPropertyListPattern(boolean value) {
        if (value) {
            _compressed_storage |= PROPLIST_PATTERN;
        } else {
            _compressed_storage &= ~PROPLIST_PATTERN;
        }
    }

    /**
     * Checks whether the object name is a pattern on the value part
     * of at least one of the key properties.
     * <p>
     * For example, "d:k=*" and "d:k=*,*" are property value patterns
     * whereas "d:k=v,*" is not.
     *
     * @return  True if the name is a property value pattern, otherwise false.
     *
     * @since 1.6
     */
    public boolean isPropertyValuePattern() {
        return (_compressed_storage & PROPVAL_PATTERN) != 0;
    }

    /**
     * Marks the object name as representing a pattern on the value part.
     * @param value {@code true} if the value part of at least one of the
     *              key properties is a pattern, {@code false} otherwise
     */
    private void setPropertyValuePattern(boolean value) {
        if (value) {
            _compressed_storage |= PROPVAL_PATTERN;
        } else {
            _compressed_storage &= ~PROPVAL_PATTERN;
        }
    }

    /**
     * Checks whether the value associated with a key in a key
     * property is a pattern.
     *
     * @param property The property whose value is to be checked.
     *
     * @return True if the value associated with the given key property
     * is a pattern, otherwise false.
     *
     * @exception NullPointerException If <code>property</code> is null.
     * @exception IllegalArgumentException If <code>property</code> is not
     * a valid key property for this ObjectName.
     *
     * @since 1.6
     */
    public boolean isPropertyValuePattern(String property) {
        if (property == null)
            throw new NullPointerException("key property can't be null");
        for (int i = 0; i < _ca_array.length; i++) {
            Property prop = _ca_array[i];
            String key = prop.getKeyString(_canonicalName);
            if (key.equals(property))
                return (prop instanceof PatternProperty);
        }
        throw new IllegalArgumentException("key property not found");
    }

    /**
     * <p>Returns the canonical form of the name; that is, a string
     * representation where the properties are sorted in lexical
     * order.</p>
     *
     * <p>More precisely, the canonical form of the name is a String
     * consisting of the <em>domain part</em>, a colon
     * (<code>:</code>), the <em>canonical key property list</em>, and
     * a <em>pattern indication</em>.</p>
     *
     * <p>The <em>canonical key property list</em> is the same string
     * as described for {@link #getCanonicalKeyPropertyListString()}.</p>
     *
     * <p>The <em>pattern indication</em> is:
     * <ul>
     * <li>empty for an ObjectName
     * that is not a property list pattern;
     * <li>an asterisk for an ObjectName
     * that is a property list pattern with no keys; or
     * <li>a comma and an
     * asterisk (<code>,*</code>) for an ObjectName that is a property
     * list pattern with at least one key.
     * </ul>
     *
     * @return The canonical form of the name.
     */
    public String getCanonicalName()  {
        return _canonicalName;
    }

    /**
     * Returns the domain part.
     *
     * @return The domain.
     */
    public String getDomain()  {
        return _canonicalName.substring(0, getDomainLength());
    }

    /**
     * Obtains the value associated with a key in a key property.
     *
     * @param property The property whose value is to be obtained.
     *
     * @return The value of the property, or null if there is no such
     * property in this ObjectName.
     *
     * @exception NullPointerException If <code>property</code> is null.
     */
    public String getKeyProperty(String property) {
        return _getKeyPropertyList().get(property);
    }

    /**
     * <p>Returns the key properties as a Map.  The returned
     * value is a Map in which each key is a key in the
     * ObjectName's key property list and each value is the associated
     * value.</p>
     *
     * <p>The returned value must not be modified.</p>
     *
     * @return The table of key properties.
     */
    private Map<String,String> _getKeyPropertyList()  {
        synchronized (this) {
            if (_propertyList == null) {
                // build (lazy eval) the property list from the canonical
                // properties array
                _propertyList = new HashMap<String,String>();
                int len = _ca_array.length;
                Property prop;
                for (int i = len - 1; i >= 0; i--) {
                    prop = _ca_array[i];
                    _propertyList.put(prop.getKeyString(_canonicalName),
                            prop.getValueString(_canonicalName));
                }
            }
        }
        return _propertyList;
    }

    /**
     * <p>Returns the key properties as a Hashtable.  The returned
     * value is a Hashtable in which each key is a key in the
     * ObjectName's key property list and each value is the associated
     * value.</p>
     *
     * <p>The returned value may be unmodifiable.  If it is
     * modifiable, changing it has no effect on this ObjectName.</p>
     *
     * @return The table of key properties.
     */
    // CR 6441274 depends on the modification property defined above
    public Hashtable<String,String> getKeyPropertyList()  {
        return new Hashtable<String,String>(_getKeyPropertyList());
    }

    /**
     * <p>Returns a string representation of the list of key
     * properties specified at creation time.  If this ObjectName was
     * constructed with the constructor {@link #ObjectName(String)},
     * the key properties in the returned String will be in the same
     * order as in the argument to the constructor.</p>
     *
     * @return The key property list string.  This string is
     * independent of whether the ObjectName is a pattern.
     */
    public String getKeyPropertyListString()  {
        // BEWARE : we rebuild the propertyliststring at each call !!
        if (_kp_array.length == 0) return "";

        // the size of the string is the canonical one minus domain
        // part and pattern part
        final int total_size = _canonicalName.length() - getDomainLength() - 1
                - (isPropertyListPattern()?2:0);

        final char[] dest_chars = new char[total_size];
        final char[] value = _canonicalName.toCharArray();
        writeKeyPropertyListString(value,dest_chars,0);
        return new String(dest_chars);
    }

    /**
     * <p>Returns the serialized string of the ObjectName.
     * properties specified at creation time.  If this ObjectName was
     * constructed with the constructor {@link #ObjectName(String)},
     * the key properties in the returned String will be in the same
     * order as in the argument to the constructor.</p>
     *
     * @return The key property list string.  This string is
     * independent of whether the ObjectName is a pattern.
     */
    private String getSerializedNameString()  {

        // the size of the string is the canonical one
        final int total_size = _canonicalName.length();
        final char[] dest_chars = new char[total_size];
        final char[] value = _canonicalName.toCharArray();
        final int offset = getDomainLength() + 1;

        // copy "domain:" into dest_chars
        //
        System.arraycopy(value, 0, dest_chars, 0, offset);

        // Add property list string
        final int end = writeKeyPropertyListString(value,dest_chars,offset);

        // Add ",*" if necessary
        if (isPropertyListPattern()) {
            if (end == offset)  {
                // Property list string is empty.
                dest_chars[end] = '*';
            } else {
                // Property list string is not empty.
                dest_chars[end]   = ',';
                dest_chars[end+1] = '*';
            }
        }

        return new String(dest_chars);
    }

    /**
     * <p>Write a string representation of the list of key
     * properties specified at creation time in the given array, starting
     * at the specified offset.  If this ObjectName was
     * constructed with the constructor {@link #ObjectName(String)},
     * the key properties in the returned String will be in the same
     * order as in the argument to the constructor.</p>
     *
     * @return offset + #of chars written
     */
    private int writeKeyPropertyListString(char[] canonicalChars,
                                           char[] data, int offset)  {
        if (_kp_array.length == 0) return offset;

        final char[] dest_chars = data;
        final char[] value = canonicalChars;

        int index = offset;
        final int len = _kp_array.length;
        final int last = len - 1;
        for (int i = 0; i < len; i++) {
            final Property prop = _kp_array[i];
            final int prop_len = prop._key_length + prop._value_length + 1;
            System.arraycopy(value, prop._key_index, dest_chars, index,
                    prop_len);
            index += prop_len;
            if (i < last ) dest_chars[index++] = ',';
        }
        return index;
    }



    /**
     * Returns a string representation of the list of key properties,
     * in which the key properties are sorted in lexical order. This
     * is used in lexicographic comparisons performed in order to
     * select MBeans based on their key property list.  Lexical order
     * is the order implied by {@link String#compareTo(String)
     * String.compareTo(String)}.
     *
     * @return The canonical key property list string.  This string is
     * independent of whether the ObjectName is a pattern.
     */
    public String getCanonicalKeyPropertyListString()  {
        if (_ca_array.length == 0) return "";

        int len = _canonicalName.length();
        if (isPropertyListPattern()) len -= 2;
        return _canonicalName.substring(getDomainLength() + 1, len);
    }
    // Category : Getter methods <===================================

    // Category : Utilities ---------------------------------------->

    /**
     * <p>Returns a string representation of the object name.  The
     * format of this string is not specified, but users can expect
     * that two ObjectNames return the same string if and only if they
     * are equal.</p>
     *
     * @return a string representation of this object name.
     */
    @Override
    public String toString()  {
        return getSerializedNameString();
    }

    /**
     * Compares the current object name with another object name.  Two
     * ObjectName instances are equal if and only if their canonical
     * forms are equal.  The canonical form is the string described
     * for {@link #getCanonicalName()}.
     *
     * @param object  The object name that the current object name is to be
     *        compared with.
     *
     * @return True if <code>object</code> is an ObjectName whose
     * canonical form is equal to that of this ObjectName.
     */
    @Override
    public boolean equals(Object object)  {

        // same object case
        if (this == object) return true;

        // object is not an object name case
        if (!(object instanceof ObjectName)) return false;

        // equality when canonical names are the same
        // (because usage of intern())
        ObjectName on = (ObjectName) object;
        String on_string = on._canonicalName;
        if (_canonicalName == on_string) return true;  // ES: OK

        // Because we are sharing canonical form between object names,
        // we have finished the comparison at this stage ==> unequal
        return false;
    }

    /**
     * Returns a hash code for this object name.
     *
     */
    @Override
    public int hashCode() {
        return _canonicalName.hashCode();
    }

    /**
     * <p>Returns a quoted form of the given String, suitable for
     * inclusion in an ObjectName.  The returned value can be used as
     * the value associated with a key in an ObjectName.  The String
     * <code>s</code> may contain any character.  Appropriate quoting
     * ensures that the returned value is legal in an ObjectName.</p>
     *
     * <p>The returned value consists of a quote ('"'), a sequence of
     * characters corresponding to the characters of <code>s</code>,
     * and another quote.  Characters in <code>s</code> appear
     * unchanged within the returned value except:</p>
     *
     * <ul>
     * <li>A quote ('"') is replaced by a backslash (\) followed by a quote.</li>
     * <li>An asterisk ('*') is replaced by a backslash (\) followed by an
     * asterisk.</li>
     * <li>A question mark ('?') is replaced by a backslash (\) followed by
     * a question mark.</li>
     * <li>A backslash ('\') is replaced by two backslashes.</li>
     * <li>A newline character (the character '\n' in Java) is replaced
     * by a backslash followed by the character '\n'.</li>
     * </ul>
     *
     * @param s the String to be quoted.
     *
     * @return the quoted String.
     *
     * @exception NullPointerException if <code>s</code> is null.
     *
     */
    public static String quote(String s) {
        final StringBuilder buf = new StringBuilder("\"");
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\n':
                    c = 'n';
                    buf.append('\\');
                    break;
                case '\\':
                case '\"':
                case '*':
                case '?':
                    buf.append('\\');
                    break;
            }
            buf.append(c);
        }
        buf.append('"');
        return buf.toString();
    }

    /**
     * <p>Returns an unquoted form of the given String.  If
     * <code>q</code> is a String returned by {@link #quote quote(s)},
     * then <code>unquote(q).equals(s)</code>.  If there is no String
     * <code>s</code> for which <code>quote(s).equals(q)</code>, then
     * unquote(q) throws an IllegalArgumentException.</p>
     *
     * <p>These rules imply that there is a one-to-one mapping between
     * quoted and unquoted forms.</p>
     *
     * @param q the String to be unquoted.
     *
     * @return the unquoted String.
     *
     * @exception IllegalArgumentException if <code>q</code> could not
     * have been returned by the {@link #quote} method, for instance
     * if it does not begin and end with a quote (").
     *
     * @exception NullPointerException if <code>q</code> is null.
     *
     */
    public static String unquote(String q) {
        final StringBuilder buf = new StringBuilder();
        final int len = q.length();
        if (len < 2 || q.charAt(0) != '"' || q.charAt(len - 1) != '"')
            throw new IllegalArgumentException("Argument not quoted");
        for (int i = 1; i < len - 1; i++) {
            char c = q.charAt(i);
            if (c == '\\') {
                if (i == len - 2)
                    throw new IllegalArgumentException("Trailing backslash");
                c = q.charAt(++i);
                switch (c) {
                    case 'n':
                        c = '\n';
                        break;
                    case '\\':
                    case '\"':
                    case '*':
                    case '?':
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Bad character '" + c + "' after backslash");
                }
            } else {
                switch (c) {
                    case '*' :
                    case '?' :
                    case '\"':
                    case '\n':
                        throw new IllegalArgumentException(
                                "Invalid unescaped character '" + c +
                                        "' in the string to unquote");
                }
            }
            buf.append(c);
        }
        return buf.toString();
    }

    /**
     * Defines the wildcard "*:*" ObjectName.
     *
     * @since 1.6
     */
    public static final ObjectName WILDCARD = newObjectName("*:*");

    // Category : Utilities <===================================

    // Category : QueryExp Interface ---------------------------------------->

    /**
     * <p>Test whether this ObjectName, which may be a pattern,
     * matches another ObjectName.  If <code>name</code> is a pattern,
     * the result is false.  If this ObjectName is a pattern, the
     * result is true if and only if <code>name</code> matches the
     * pattern.  If neither this ObjectName nor <code>name</code> is
     * a pattern, the result is true if and only if the two
     * ObjectNames are equal as described for the {@link
     * #equals(Object)} method.</p>
     *
     * @param name The name of the MBean to compare to.
     *
     * @return True if <code>name</code> matches this ObjectName.
     *
     * @exception NullPointerException if <code>name</code> is null.
     *
     */
    public boolean apply(ObjectName name) {

        if (name == null) throw new NullPointerException();

        if (name.isPattern())
            return false;

        // No pattern
        if (!isPattern())
            return _canonicalName.equals(name._canonicalName);

        return matchDomains(name) && matchKeys(name);
    }

    private final boolean matchDomains(ObjectName name) {
        if (isDomainPattern()) {
            // wildmatch domains
            // This ObjectName is the pattern
            // The other ObjectName is the string.
            return wildmatch(name.getDomain(),getDomain());
        }
        return getDomain().equals(name.getDomain());
    }

    private boolean wildmatch(String domain, String domain1) {
        return false;
    }

    private final boolean matchKeys(ObjectName name) {
        // If key property value pattern but not key property list
        // pattern, then the number of key properties must be equal
        //
        if (isPropertyValuePattern() &&
                !isPropertyListPattern() &&
                (name._ca_array.length != _ca_array.length))
            return false;

        // If key property value pattern or key property list pattern,
        // then every property inside pattern should exist in name
        //
        if (isPropertyPattern()) {
            final Map<String,String> nameProps = name._getKeyPropertyList();
            final Property[] props = _ca_array;
            final String cn = _canonicalName;
            for (int i = props.length - 1; i >= 0 ; i--) {
                // Find value in given object name for key at current
                // index in receiver
                //
                final Property p = props[i];
                final String   k = p.getKeyString(cn);
                final String   v = nameProps.get(k);
                // Did we find a value for this key ?
                //
                if (v == null) return false;
                // If this property is ok (same key, same value), go to next
                //
                if (isPropertyValuePattern() && (p instanceof PatternProperty)) {
                    // wildmatch key property values
                    // p is the property pattern, v is the string
                    if (wildmatch(v,p.getValueString(cn)))
                        continue;
                    else
                        return false;
                }
                if (v.equals(p.getValueString(cn))) continue;
                return false;
            }
            return true;
        }

        // If no pattern, then canonical names must be equal
        //
        final String p1 = name.getCanonicalKeyPropertyListString();
        final String p2 = getCanonicalKeyPropertyListString();
        return (p1.equals(p2));
    }

    /* Method inherited from QueryExp, no implementation needed here
       because ObjectName is not relative to an MBeanServer and does
       not contain a subquery.
    */
   // public void setMBeanServer(MBeanServer mbs) { }

    // Category : QueryExp Interface <=========================

    // Category : Comparable Interface ---------------------------------------->

    /**
     * <p>Compares two ObjectName instances. The ordering relation between
     * ObjectNames is not completely specified but is intended to be such
     * that a sorted list of ObjectNames will appear in an order that is
     * convenient for a person to read.</p>
     *
     * <p>In particular, if the two ObjectName instances have different
     * domains then their order is the lexicographical order of the domains.
     * The ordering of the key property list remains unspecified.</p>
     *
     * <p>For example, the ObjectName instances below:</p>
     * <ul>
     * <li>Shapes:type=Square,name=3</li>
     * <li>Colors:type=Red,name=2</li>
     * <li>Shapes:type=Triangle,side=isosceles,name=2</li>
     * <li>Colors:type=Red,name=1</li>
     * <li>Shapes:type=Square,name=1</li>
     * <li>Colors:type=Blue,name=1</li>
     * <li>Shapes:type=Square,name=2</li>
     * <li>JMImplementation:type=MBeanServerDelegate</li>
     * <li>Shapes:type=Triangle,side=scalene,name=1</li>
     * </ul>
     * <p>could be ordered as follows:</p>
     * <ul>
     * <li>Colors:type=Blue,name=1</li>
     * <li>Colors:type=Red,name=1</li>
     * <li>Colors:type=Red,name=2</li>
     * <li>JMImplementation:type=MBeanServerDelegate</li>
     * <li>Shapes:type=Square,name=1</li>
     * <li>Shapes:type=Square,name=2</li>
     * <li>Shapes:type=Square,name=3</li>
     * <li>Shapes:type=Triangle,side=scalene,name=1</li>
     * <li>Shapes:type=Triangle,side=isosceles,name=2</li>
     * </ul>
     *
     * @param name the ObjectName to be compared.
     *
     * @return a negative integer, zero, or a positive integer as this
     *         ObjectName is less than, equal to, or greater than the
     *         specified ObjectName.
     *
     * @since 1.6
     */
    public int compareTo(ObjectName name) {
        // Quick optimization:
        //
        if (name == this) return 0;

        // (1) Compare domains
        //
        int domainValue = this.getDomain().compareTo(name.getDomain());
        if (domainValue != 0)
            return domainValue;

        // (2) Compare "type=" keys
        //
        // Within a given domain, all names with missing or empty "type="
        // come before all names with non-empty type.
        //
        // When both types are missing or empty, canonical-name ordering
        // applies which is a total order.
        //
        String thisTypeKey = this.getKeyProperty("type");
        String anotherTypeKey = name.getKeyProperty("type");
        if (thisTypeKey == null)
            thisTypeKey = "";
        if (anotherTypeKey == null)
            anotherTypeKey = "";
        int typeKeyValue = thisTypeKey.compareTo(anotherTypeKey);
        if (typeKeyValue != 0)
            return typeKeyValue;

        // (3) Compare canonical names
        //
        return this.getCanonicalName().compareTo(name.getCanonicalName());
    }

    // Category : Comparable Interface <=========================

    // Public methods <========================================

}
