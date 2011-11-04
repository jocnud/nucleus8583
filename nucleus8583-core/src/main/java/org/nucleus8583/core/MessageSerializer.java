package org.nucleus8583.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.nucleus8583.core.field.type.FieldType;
import org.nucleus8583.core.field.type.FieldTypes;
import org.nucleus8583.core.util.BitmapHelper;
import org.nucleus8583.core.util.ResourceUtils;
import org.nucleus8583.core.xml.FieldDefinition;
import org.nucleus8583.core.xml.MessageDefinition;
import org.nucleus8583.core.xml.MessageDefinitionReader;
import org.w3c.dom.Node;

/**
 * Serialize/deserialize {@link Message} object. Creating this class requires
 * configuration.
 *
 * @author Robbi Kurniawan
 *
 */
public final class MessageSerializer {

    private static final Comparator<FieldType> ORDER_BY_ID_ASC = new Comparator<FieldType>() {

        public int compare(FieldType a, FieldType b) {
            return a.getId() - b.getId();
        }
    };

    /**
     * same as <code>
     *     return new MessageSerializer(location);
     * </code>
     *
     * @param location
     * @return a new instance of MessageSerializer.
     */
    public static MessageSerializer create(String location) {
        return new MessageSerializer(location);
    }

    /**
     * same as <code>
     *     return new MessageSerializer(in);
     * </code>
     *
     * @param in
     * @return a new instance of MessageSerializer.
     */
    public static MessageSerializer create(InputStream in) {
        return new MessageSerializer(in);
    }

    /**
     * same as <code>
     *     return new MessageSerializer(node);
     * </code>
     *
     * @param node
     * @return a new instance of MessageSerializer.
     */
    public static MessageSerializer create(Node node) {
        return new MessageSerializer(node);
    }

    private boolean hasMti;

    private FieldType[] fields;

    private boolean[] binaries;

    private int fieldsCount;

    /**
     * create a new instance of {@link MessageSerializer} using given
     * configuration.
     *
     * For example, if you want to load "nucleus8583.xml" from "META-INF"
     * located in classpath, the location should be
     * <code>classpath:META-INF/nucleus8583.xml</code>.
     *
     * If you want to load "nucleus8583.xml" from "conf" directory, the location
     * should be <code>file:conf/nucleus8583.xml</code> or just
     * <code>conf/nucleus8583.xml</code>.
     *
     * @param location
     *            configuration location (in URI)
     */
    public MessageSerializer(String location) {
        URL found = ResourceUtils.getURL(location);
        if (found == null) {
            throw new RuntimeException("unable to find " + location);
        }

        MessageDefinition definition;

        try {
            definition = new MessageDefinitionReader().unmarshal(found);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        init(definition);
    }

    /**
     * create a new instance of {@link MessageSerializer} using given
     * configuration
     *
     * @param in
     *            input stream
     */
    public MessageSerializer(InputStream in) {
        MessageDefinition definition;

        try {
            definition = new MessageDefinitionReader().unmarshal(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        init(definition);
    }

    /**
     * create a new instance of {@link MessageSerializer} using given
     * configuration
     *
     * @param node
     *            an DOM node where the entire XML start from
     */
    public MessageSerializer(Node node) {
        MessageDefinition definition;

        try {
            definition = new MessageDefinitionReader().unmarshal(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        init(definition);
    }

    private boolean setIfAbsent(int id, FieldDefinition value, List<FieldDefinition> fields, int count) {
        for (int i = 0; i < count; ++i) {
            FieldDefinition def = fields.get(i);

            if (def.getId() == id) {
                return false;
            }
        }

        fields.add(value);
        return true;
    }

    private void checkDuplicateId(List<FieldDefinition> list, int count) {
        Set<Integer> set = new HashSet<Integer>();

        for (int i = 0; i < count; ++i) {
            Integer id = Integer.valueOf(list.get(i).getId());

            if (set.contains(id)) {
                throw new IllegalArgumentException("duplicate id " + id + " found");
            }

            set.add(id);
        }
    }

    private void init(MessageDefinition definition) {
        List<FieldDefinition> fields = definition.getFields();
        int count = fields.size();

        hasMti = !setIfAbsent(0, FieldDefinition.FIELD_0, fields, count);

        // set field 1 if absent
        setIfAbsent(1, FieldDefinition.FIELD_1, fields, count);

        // set field 65 if absent
        setIfAbsent(65, FieldDefinition.FIELD_65, fields, count);

        checkDuplicateId(fields, count);

        fieldsCount = fields.size();
        this.fields = new FieldType[fieldsCount];

        FieldTypes.initialize();

        for (int i = 0; i < fieldsCount; ++i) {
            try {
                this.fields[i] = FieldTypes.getType(fields.get(i));
            } catch (RuntimeException ex) {
                throw new RuntimeException("unable to instantiate iso field number " + fields.get(i).getId(), ex);
            }
        }

        // sort fields by it's id
        Arrays.sort(this.fields, ORDER_BY_ID_ASC);

        // check for skipped fields
        for (int i = hasMti ? 1 : 0; i < fieldsCount; ++i) {
            if (this.fields[i].getId() != i) {
                throw new IllegalArgumentException("field #" + i + " is not defined");
            }
        }

        binaries = new boolean[fieldsCount];
        for (int i = fieldsCount - 1; i >= 0; --i) {
            binaries[i] = this.fields[i].isBinary();
        }
    }

    /**
     * read serialized data from buffer and set it's values to given
     * {@link Message} object
     *
     * @param buf
     *            The buffer
     * @param out
     *            The {@link Message} object
     * @throws IOException
     *             thrown if the buffer length is shorter than expected.
     */
    public void read(byte[] buf, Message out) throws IOException {
        read(new ByteArrayInputStream(buf), out);
    }

    /**
     * read serialized data from stream and set it's values to given
     * {@link Message} object
     *
     * @param in
     *            The stream
     * @param out
     *            The {@link Message} object
     * @throws IOException
     *             thrown if an IO error occurred while serializing.
     */
    public void read(InputStream in, Message out) throws IOException {
        byte[] bits1To128 = out.directBits1To128();
        byte[] bits129To192 = out.directBits129To192();

        int count = out.size();
        if (count > fieldsCount) {
            count = fieldsCount;
        }

        int i = 0;
        try {
            if (hasMti) {
                // read bit-0
                out.setMti(fields[0].readString(in));
            }

            // read bit-1
            i = 1;
            fields[1].read(in, bits1To128, 0, 8);

            if (BitmapHelper.get(bits1To128, 0)) {
                fields[1].read(in, bits1To128, 8, 8);
            }

            // read bit-i
            i = 2;
            for (int iMin1 = 1, iMin129 = -127; i < count; ++i, ++iMin1, ++iMin129) {
                if (i == 65) {
                    if (BitmapHelper.get(bits1To128, 64)) {
                        fields[i].read(in, bits129To192, 0, 8);
                    }
                } else if (i < 129) {
                    if (BitmapHelper.get(bits1To128, iMin1)) {
                        if (binaries[i]) {
                            out.unsafeSet(i, fields[i].readBinary(in));
                        } else {
                            out.unsafeSet(i, fields[i].readString(in));
                        }
                    }
                } else {
                    if (BitmapHelper.get(bits129To192, iMin129)) {
                        if (binaries[i]) {
                            out.unsafeSet(i, fields[i].readBinary(in));
                        } else {
                            out.unsafeSet(i, fields[i].readString(in));
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new IOException("unable to read field #" + i, ex);
        } catch (RuntimeException ex) {
            throw new RuntimeException("unable to read field #" + i, ex);
        }
    }

    /**
     * serialize {@link Message} object into internal byte buffer and return the
     * buffer
     *
     * @param msg
     *            The {@link Message} object
     */
    public byte[] write(Message msg) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            write(msg, out);
        } catch (IOException e) {
            // never been here
        }

        return out.toByteArray();
    }

    /**
     * serialize {@link Message} object into given stream
     *
     * @param msg
     *            The {@link Message} object
     * @param out
     *            The stream
     * @throws IOException
     *             thrown if an IO error occurred while serializing.
     */
    public void write(Message msg, OutputStream out) throws IOException {
        byte[] bits1To128 = msg.directBits1To128();
        byte[] bits129To192 = msg.directBits129To192();

        byte[][] binaryValues = msg.directBinaryValues();
        String[] stringValues = msg.directStringValues();

        // is bit 1 on?
        boolean bit1IsOn = false;

        if (BitmapHelper.realBytesInUse(bits1To128) > 8) {
            BitmapHelper.set(bits1To128, 0);
            bit1IsOn = true;
        } else {
            BitmapHelper.clear(bits1To128, 0);
        }

        // is bit 65 on?
        if (BitmapHelper.isEmpty(bits129To192)) {
            BitmapHelper.clear(bits1To128, 64);

            binaryValues[65] = null;
            stringValues[65] = null;
        } else {
            if (!bit1IsOn) {
                BitmapHelper.set(bits1To128, 0); // bit 1 must be on
                bit1IsOn = true;
            }
            BitmapHelper.set(bits1To128, 64);

            binaryValues[65] = bits129To192;
            stringValues[65] = null;
        }

        int count = msg.size();
        if (count > fieldsCount) {
            count = fieldsCount;
        }

        int i = 0;
        try {
            // write bit 0
            if (hasMti) {
                fields[0].write(out, msg.getMti());
            }

            // write bit 1
            i = 1;
            fields[1].write(out, bits1To128, 0, bit1IsOn ? 16 : 8);

            // write bit i
            i = 2;
            for (int j = 1; (i < count) && (i < 129); ++i, ++j) {
                if (BitmapHelper.get(bits1To128, j)) {
                    if (i == 65) {
                        fields[i].write(out, binaryValues[i], 0, 8);
                    } else {
                        if (binaries[i]) {
                            fields[i].write(out, binaryValues[i]);
                        } else {
                            fields[i].write(out, stringValues[i]);
                        }
                    }
                }
            }

            i = 129;
            for (int j = 0; i < count; ++i, ++j) {
                if (BitmapHelper.get(bits129To192, j)) {
                    if (binaries[i]) {
                        fields[i].write(out, binaryValues[i]);
                    } else {
                        fields[i].write(out, stringValues[i]);
                    }
                }
            }
        } catch (RuntimeException ex) {
            throw new RuntimeException("unable to write field #" + i, ex);
        }
    }
}
