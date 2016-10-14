package org.verapdf.gf.model.impl.cos;

import org.apache.log4j.Logger;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.*;
import org.verapdf.gf.model.impl.containers.StaticContainers;
import org.verapdf.gf.model.impl.pd.GFPDDocument;
import org.verapdf.gf.model.impl.pd.util.XMPChecker;
import org.verapdf.model.baselayer.Object;
import org.verapdf.model.coslayer.*;
import org.verapdf.pd.PDNameTreeNode;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.util.*;

/**
 * @author Timur Kamalov
 */
public class GFCosDocument extends GFCosObject implements CosDocument {

    private static final Logger LOGGER = Logger.getLogger(GFCosDocument.class);

    /** Type name for GFCosDocument */
    public static final String COS_DOCUMENT_TYPE = "CosDocument";

    private static final String TRAILER = "trailer";
    private static final String XREF = "xref";
    private static final String INDIRECT_OBJECTS = "indirectObjects";
    private static final String DOCUMENT = "document";
    private static final String EMBEDDED_FILES = "EmbeddedFiles";
    private static final String ID = "ID";
    private static final String REQUIREMENTS = "Requirements";

    private final COSDictionary catalog;

    private final long indirectObjectCount;
    private final float version;
    private final long headerOffset;
    private final String header;
    private final int headerCommentByte1;
    private final int headerCommentByte2;
    private final int headerCommentByte3;
    private final int headerCommentByte4;
    private final boolean isOptionalContentPresent;
    private final int postEOFDataSize;
    private final boolean isLinearised;
    private final String firstPageID;
    private final String lastID;

    /**
     * Constructor using greenfield COSDocument
     * @param cosDocument greenfield COSDocument
     */
    public GFCosDocument(COSDocument cosDocument) {
        super(cosDocument, COS_DOCUMENT_TYPE);
        this.catalog = this.getCatalog();

        COSHeader cosHeader = cosDocument.getHeader();
        this.indirectObjectCount = cosDocument.getObjects().size();
        this.version = cosHeader.getVersion();
        this.headerOffset = cosHeader.getHeaderOffset();
        this.header = cosHeader.getHeader();
        this.headerCommentByte1 = cosHeader.getHeaderCommentByte1();
        this.headerCommentByte2 = cosHeader.getHeaderCommentByte2();
        this.headerCommentByte3 = cosHeader.getHeaderCommentByte3();
        this.headerCommentByte4 = cosHeader.getHeaderCommentByte4();
        this.isOptionalContentPresent = parseOptionalContentPresent();
        this.postEOFDataSize = cosDocument.getPostEOFDataSize();
        this.isLinearised = cosDocument.getTrailer() != cosDocument
                .getLastTrailer() && cosDocument.isLinearized();
        this.lastID = getTrailerID(cosDocument.getLastTrailer().getKey(ASAtom.ID));
        this.firstPageID = getTrailerID(cosDocument.getFirstTrailer().getKey(ASAtom.ID));
    }

    private boolean parseOptionalContentPresent() {
        return this.catalog != null &&
                this.catalog.getKey(ASAtom.OCPROPERTIES) != COSObject.getEmpty();
    }

    /**
     * Number of indirect objects in the document
     */
    @Override
    public Long getnrIndirects() {
        return Long.valueOf(this.indirectObjectCount);
    }

    /**
     * @return version of pdf document
     */
    @Override
    public Double getversion() {
        return Double.valueOf(this.version);
    }

    @Override
    public Long getheaderOffset() {
        return this.headerOffset;
    }

    @Override
    public String getheader() {
        return this.header;
    }

    @Override
    public Long getheaderByte1() {
        return Long.valueOf(this.headerCommentByte1);
    }

    @Override
    public Long getheaderByte2() {
        return Long.valueOf(this.headerCommentByte2);
    }

    @Override
    public Long getheaderByte3() {
        return Long.valueOf(this.headerCommentByte3);
    }

    @Override
    public Long getheaderByte4() {
        return Long.valueOf(this.headerCommentByte4);
    }

    /**
     * true if catalog contain OCProperties key
     */
    @Override
    public Boolean getisOptionalContentPresent() {
        return isOptionalContentPresent;
    }

    /**
     * EOF must complies PDF/A standard
     */
    @Override
    public Long getpostEOFDataSize() {
        return Long.valueOf(this.postEOFDataSize);
    }

    /**
     * @return ID of first page trailer
     */
    @Override
    public String getfirstPageID() {
        return this.firstPageID;
    }

    /**
     * @return ID of last document trailer
     */
    @Override
    public String getlastID() {
        if (StaticContainers.getFlavour().getPart().equals(PDFAFlavour.Specification.ISO_19005_1)) {
            return this.lastID;
        } else if (this.isLinearised) {
            return this.firstPageID;
        } else {
            return this.lastID;
        }
    }

    private static String getTrailerID(COSObject ids) {
        if (ids != null && ids.getType() == COSObjType.COS_ARRAY) {
            COSArray idArray = (COSArray) ids.get();
            StringBuilder builder = new StringBuilder();
            for (COSObject id : idArray) {
                for (byte aByte : ((COSString) id.get()).get().getBytes()) {
                    builder.append((char) (aByte & 0xFF));
                }
            }
            // need to discard last whitespace
            return builder.toString();
        }
        return null;
    }

    /**
     * @return true if the current document is linearized
     */
    @Override
    public Boolean getisLinearized() {
        return this.isLinearised;
    }

    /**
     * @return true if XMP content matches Info dictionary content
     */
    @Override
    public Boolean getdoesInfoMatchXMP() {
        return XMPChecker.doesInfoMatchXMP(cosDocument);
    }

    @Override
    public Boolean getMarked() {
        if (this.catalog != null) {
            COSObject markInfoObject = this.catalog.getKey(ASAtom.MARK_INFO);
            if (markInfoObject == null || markInfoObject.empty()) {
                return Boolean.FALSE;
            } else {
                COSBase markInfo = markInfoObject.getDirectBase();
                if (markInfo.getType() == COSObjType.COS_DICT) {
                    return markInfo.getBooleanKey(ASAtom.MARKED);
                } else {
                    LOGGER.warn("MarkedInfo must be a 'COSDictionary' but got: "
                            + markInfoObject.getType());
                    return Boolean.FALSE;
                }
            }
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public String getRequirements() {
        if (this.catalog != null) {
            COSObject reqArrayObject = this.catalog.getKey(ASAtom.REQUIREMENTS);
            if (reqArrayObject != null && !reqArrayObject.empty()) {
                COSBase reqArray = reqArrayObject.getDirectBase();
                if (reqArray.getType() == COSObjType.COS_ARRAY) {
                    return this.getRequirementsString((COSArray) reqArray);
                }
            }
        }
        return null;
    }

    private String getRequirementsString(COSArray reqArray) {
        String result = "";
        Iterator iterator = reqArray.iterator();
        while (iterator.hasNext()) {
            COSObject element = (COSObject) iterator.next();
            COSBase base = element.getDirectBase();
            if (base.getType() == COSObjType.COS_DICT) {
                String sKey = element.getStringKey(ASAtom.S);
                result += sKey;
                result += " ";
            }
        }
        return result;
    }

    /**
     * @return true if {@code NeedsRendering} entry in catalog contains
     * {@code true} value.
     */
    @Override
    public Boolean getNeedsRendering() {
        if(!catalog.knownKey(ASAtom.NEEDS_RENDERING)) {
            return Boolean.valueOf(false);
        }
        return catalog.getBooleanKey(ASAtom.NEEDS_RENDERING);
    }

    @Override
    public List<? extends Object> getLinkedObjects(String link) {
        switch (link) {
            case TRAILER:
                return this.getTrailer();
            case INDIRECT_OBJECTS:
                return this.getIndirectObjects();
            case DOCUMENT:
                return this.getDocument();
            case XREF:
                return this.getXRefs();
            case EMBEDDED_FILES:
                return this.getEmbeddedFiles();
            default:
                return super.getLinkedObjects(link);
        }
    }

    /**
     * @return list of embedded files
     */
    private List<CosFileSpecification> getEmbeddedFiles() {
        if (this.catalog != null) {
            COSObject buffer = this.catalog.getKey(ASAtom.NAMES);
            if (!buffer.empty()) {
                COSObject base = buffer.getKey(ASAtom.EMBEDDED_FILES);
                if (base != null && base.getType() == COSObjType.COS_DICT) {
                    List<CosFileSpecification> files = new ArrayList<>();
                    this.getNamesEmbeddedFiles(files, PDNameTreeNode.create(base));
                    return Collections.unmodifiableList(files);
                }
            }
        }
        return Collections.emptyList();
    }

    private void getNamesEmbeddedFiles(List<CosFileSpecification> files,
                                       PDNameTreeNode node) {
        Map<String, COSObject> names = node.getNames();
        for (COSObject value : names.values()) {
            if (value != null && value.getType().isDictionaryBased()) {
                files.add(new GFCosFileSpecification((COSDictionary) value.getDirectBase()));
            }
        }
        for (PDNameTreeNode kid : node.getKids()) {
            getNamesEmbeddedFiles(files, kid);
        }
    }

    /**
     * trailer dictionary
     */
    private List<CosTrailer> getTrailer() {
        List<CosTrailer> list = new ArrayList<>(MAX_NUMBER_OF_ELEMENTS);
        list.add(new GFCosTrailer((COSDictionary) cosDocument.getTrailer().getObject().get()));
        return Collections.unmodifiableList(list);
    }

    /**
     * all indirect objects referred from the xref table
     */
    private List<CosIndirect> getIndirectObjects() {
        Map<COSKey, COSObject> objects = cosDocument.getObjectsMap();
        List<CosIndirect> list = new ArrayList<>(objects.size());
        for (Map.Entry<COSKey, COSObject> entry : objects.entrySet()) {
            list.add(new GFCosIndirect(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * link to the high-level PDF Document structure
     */
    private List<org.verapdf.model.pdlayer.PDDocument> getDocument() {
        if(StaticContainers.getDocument() != null) {
            List<org.verapdf.model.pdlayer.PDDocument> list =
                    new ArrayList<>(MAX_NUMBER_OF_ELEMENTS);
            list.add(new GFPDDocument(StaticContainers.getDocument()));
            return Collections.unmodifiableList(list);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * link to cross reference table properties
     */
    private List<CosXRef> getXRefs() {
        List<CosXRef> list = new ArrayList<>(MAX_NUMBER_OF_ELEMENTS);
        list.add(new GFCosXRef(cosDocument.isSubsectionHeaderSpaceSeparated(),
                               cosDocument.isXrefEOLMarkersComplyPDFA()));
        return Collections.unmodifiableList(list);
    }

    private COSDictionary getCatalog() {
        COSBase catalogLocal = cosDocument.getTrailer().getRoot().getDirectBase();
        return catalogLocal.getType() == COSObjType.COS_DICT ? (COSDictionary) catalogLocal : null;
    }

}