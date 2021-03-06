/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.xds2.registry.ws;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.xml.bind.JAXBElement;
import javax.xml.ws.WebServiceContext;

import org.dcm4che3.net.Device;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.common.XDSUtil;
import org.dcm4chee.xds2.common.audit.AuditRequestInfo;
import org.dcm4chee.xds2.common.audit.XDSAudit;
import org.dcm4chee.xds2.common.code.AffinityDomainCodes;
import org.dcm4chee.xds2.common.code.XADCfgRepository;
import org.dcm4chee.xds2.common.exception.XDSException;
import org.dcm4chee.xds2.conf.XdsRegistry;
import org.dcm4chee.xds2.infoset.rim.AdhocQueryRequest;
import org.dcm4chee.xds2.infoset.rim.AdhocQueryResponse;
import org.dcm4chee.xds2.infoset.rim.AssociationType1;
import org.dcm4chee.xds2.infoset.rim.ClassificationNodeType;
import org.dcm4chee.xds2.infoset.rim.ClassificationSchemeType;
import org.dcm4chee.xds2.infoset.rim.ClassificationType;
import org.dcm4chee.xds2.infoset.rim.ExternalIdentifierType;
import org.dcm4chee.xds2.infoset.rim.ExtrinsicObjectType;
import org.dcm4chee.xds2.infoset.rim.IdentifiableType;
import org.dcm4chee.xds2.infoset.rim.ObjectFactory;
import org.dcm4chee.xds2.infoset.rim.ObjectRefType;
import org.dcm4chee.xds2.infoset.rim.RegistryObjectType;
import org.dcm4chee.xds2.infoset.rim.RegistryPackageType;
import org.dcm4chee.xds2.infoset.rim.RegistryResponseType;
import org.dcm4chee.xds2.infoset.rim.RemoveObjectsRequest;
import org.dcm4chee.xds2.infoset.rim.SubmitObjectsRequest;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.dcm4chee.xds2.persistence.Association;
import org.dcm4chee.xds2.persistence.Identifiable;
import org.dcm4chee.xds2.persistence.QIdentifiable;
import org.dcm4chee.xds2.persistence.QRegistryObject;
import org.dcm4chee.xds2.persistence.RegistryObject;
import org.dcm4chee.xds2.persistence.Slot;
import org.dcm4chee.xds2.persistence.XADIssuer;
import org.dcm4chee.xds2.persistence.XADPatient;
import org.dcm4chee.xds2.persistence.XDSCode;
import org.dcm4chee.xds2.persistence.XDSDocumentEntry;
import org.dcm4chee.xds2.persistence.XDSFolder;
import org.dcm4chee.xds2.persistence.XDSObject;
import org.dcm4chee.xds2.persistence.XDSSubmissionSet;
import org.dcm4chee.xds2.registry.ws.query.StoredQuery;
import org.dcm4chee.xds2.common.deactivatable.DeactivateableByConfiguration;
import org.dcm4chee.xds2.tool.init.XDSInitCommon;
import org.dcm4chee.xds2.ws.handler.LogHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysema.query.jpa.impl.JPAQuery;

@Stateless
@DeactivateableByConfiguration(extension = XdsRegistry.class)
public class XDSRegistryBean implements XDSRegistryBeanLocal {

    private static final String UNKNOWN = "UNKNOWN";

    private ObjectFactory factory = new ObjectFactory();
    
    AffinityDomainCodes adCodes;

    @Resource
    private WebServiceContext wsContext;

    @Resource 
    private SessionContext context;

    @Resource
    private EJBContext ejbContext;
    
    @PersistenceContext(unitName = "dcm4chee-xds")
    private EntityManager em;
    
    /**
     * Used to call methods in a nested transaction
     */
    @EJB
    XDSRegistryBeanLocal self;
    
    @Inject
    private Device device;
    private XdsRegistry cfg;
    
    @PostConstruct
    private void init() {
    	cfg = device.getDeviceExtension(XdsRegistry.class);
    }

    private static final Logger log = LoggerFactory.getLogger(XDSRegistryBean.class);
    
    public XDSRegistryBean() {
    }
    
    @Override
    public RegistryResponseType documentRegistryRegisterDocumentSetB(
            SubmitObjectsRequest req) {
        log.info("XDS.b - documentRegistryRegisterDocumentSetB called");
        RegistryResponseType rsp = factory.createRegistryResponseType();
        XDSPersistenceWrapper wrapper = new XDSPersistenceWrapper(this);
        try {
            preMetadataCheck(req);
            store(req, wrapper);
            rsp.setStatus(XDSConstants.XDS_B_STATUS_SUCCESS);
        } catch (Exception x) {
            XDSException e;
            if (x instanceof XDSException) {
                e = (XDSException) x;
                log.error("XDSException: {}", e.getMessage());
                log.debug("XDSException stacktrace:", x);
            } else {
                // to prevent the disclosure of internal system details, we only return the user an error uuid
                // that can be later found in the log by support team
                String erruuid = "xdserror:"+UUID.randomUUID().toString();
                log.error("Unexpected error in XDS service (register document), error id = "+erruuid, x);
                e = new XDSException(XDSException.XDS_ERR_REGISTRY_ERROR, 
                        "Unexpected error in XDS service, error id = "+erruuid, null);
            }
            XDSUtil.addError(rsp, e);
            ejbContext.setRollbackOnly();
        }

        log.info("XDS.b - documentRegistryRegisterDocumentSetB finished");

        // send audit log message
        String[] submUIDandPat;
        submUIDandPat = this.getSubmissionUIDandPatID(req);
        XDSAudit.logRegistryImport(submUIDandPat[0], submUIDandPat[1],
                new AuditRequestInfo(LogHandler.getInboundSOAPHeader(), wsContext),
                XDSConstants.XDS_B_STATUS_SUCCESS.equals(rsp.getStatus()));

        return rsp;
    }
    
    @Override
    public AdhocQueryResponse documentRegistryRegistryStoredQuery(
            AdhocQueryRequest req) {
        log.info("XDS.b - documentRegistryRegistryStoredQuery called");
        log.debug("ReturnType: {}", (req.getResponseOption() == null ? "(no response option)" : req.getResponseOption().getReturnType()));
        AdhocQueryResponse rsp;
        StoredQuery qry = null;
        try {
            qry = StoredQuery.getStoredQuery(req, this);
            rsp = qry.query();
        } catch (Exception x) {
    
            XDSException e;
            if (x instanceof XDSException) {
                e = (XDSException) x;
                log.error("XDSException: "+e.getMessage());
                log.debug("XDSException stacktrace:", x);
            } else {
                // to prevent the disclosure of internal system details, we only return the user an error uuid
                // that can be later found in the log by support team
                String erruuid = "xdserror:"+UUID.randomUUID().toString();
                log.error("Unexpected error in XDS service (query), error id = "+erruuid, x);
                e = new XDSException(XDSException.XDS_ERR_REGISTRY_ERROR, 
                        "Unexpected error in XDS service, error id = "+erruuid, null);
            }
            
            rsp = factory.createAdhocQueryResponse();
            XDSUtil.addError(rsp, e);
            rsp.setRegistryObjectList(factory.createRegistryObjectListType());
            
        }
    
        log.info("XDS.b - documentRegistryRegistryStoredQuery finished");

        // send audit log message
        XDSAudit.logRegistryQuery(req, new AuditRequestInfo(LogHandler.getInboundSOAPHeader(), wsContext),
                XDSConstants.XDS_B_STATUS_SUCCESS.equals(rsp.getStatus()));

        return rsp;
    }

    public XdsRegistry getXdsRegistryConfig() {
    	return cfg;
    }

    // TODO: use jxpath...?
    private String[] getSubmissionUIDandPatID(SubmitObjectsRequest req) {
        String[] result = new String[]{UNKNOWN, UNKNOWN};
        List<JAXBElement<? extends IdentifiableType>> objs = req.getRegistryObjectList().getIdentifiable();
        IdentifiableType obj;
        whole: for (int i=0,len=objs.size() ; i < len ; i++) {
            obj = objs.get(i).getValue();
            if (obj instanceof RegistryPackageType) {
                List<ExternalIdentifierType> list = ((RegistryPackageType)obj).getExternalIdentifier();
                if (list != null) {
                    for (ExternalIdentifierType eiType : list) {
                        if (XDSConstants.UUID_XDSSubmissionSet_patientId.equals(eiType.getIdentificationScheme())) {
                            if (eiType.getValue() != null)
                                result[1] = eiType.getValue();
                        } else if (XDSConstants.UUID_XDSSubmissionSet_uniqueId.equals(eiType.getIdentificationScheme())) {
                            if (eiType.getValue() != null)
                                result[0] = eiType.getValue();
                        } else {
                            continue;
                        }
                        if (result[0] != UNKNOWN && result[1] != UNKNOWN)
                            break whole;
                    }
                }

            }
        }
        return result;
    }

    private void preMetadataCheck(SubmitObjectsRequest req) throws XDSException {
        List<JAXBElement<? extends IdentifiableType>> objs = req.getRegistryObjectList().getIdentifiable();
        IdentifiableType obj;
        List<ExternalIdentifierType> list;
        String patID = null, patIDtmp;
        String[] mimes = cfg.getAcceptedMimeTypes();
        XADPatient xadPatient = null;
        objLoop: for (int i=0,len=objs.size() ; i < len ; i++) {
            obj = objs.get(i).getValue();
            if (obj instanceof RegistryObjectType) {
                if (mimes != null && cfg.isCheckMimetype() && (obj instanceof ExtrinsicObjectType) ) {
                    checkMimetype((ExtrinsicObjectType)obj, mimes);
                }

                list = ((RegistryObjectType)obj).getExternalIdentifier();
                if (list != null) {
                    for (ExternalIdentifierType eiType : list) {
                        patIDtmp = getPatID(eiType);
                        if (patIDtmp != null) {
                            if (patID == null) {
                                patID = patIDtmp;
                            } else if (!patIDtmp.equals(patID)) {
                                throw new XDSException(XDSException.XDS_ERR_PATID_DOESNOT_MATCH,
                                        "PatientID of object"+obj.getId()+" does not match:"+patIDtmp+" vs. "+patID, null);
                            }
                        }
                    }
                }
            }
            if (obj instanceof AssociationType1) {
                AssociationType1 assoc = (AssociationType1) obj;
                if (assoc.getSourceObject().startsWith("urn:") || assoc.getTargetObject().startsWith("urn:")) {
                    try {
                        xadPatient = new XADPatient(patID);
                    } catch (Exception ignore) {
                        throw new XDSException(XDSException.XDS_ERR_PATID_DOESNOT_MATCH,
                                "PatientID referenced in Association "+obj.getId()+" does not match! SubmissionSet patID not valid!:"+patID, null);
                    }
                }
            }
        }
        if (xadPatient == null && patID != null) {
            try {
                xadPatient = new XADPatient(patID);
            } catch (Exception ignore) {
                throw new XDSException(XDSException.XDS_ERR_REGISTRY_METADATA_ERROR, 
                        "PatientID not valid! patID:"+patID, null);
            }
        }
        if (xadPatient != null) {
            //save affinityDomain and AffinityDomainCodes in session
            String affinityDomain = xadPatient.getIssuerOfPatientID().getUniversalID();
            XADCfgRepository codeRepository = cfg.getCodeRepository();
            adCodes = codeRepository.getAffinityDomainCodes(affinityDomain);
            if (adCodes.isEmpty() && !cfg.isCreateMissingCodes()) {
                throw new XDSException(XDSException.XDS_ERR_REGISTRY_ERROR, 
                        "No codes configured for affinity domain! affinityDomain:"+affinityDomain, null);
            }
            checkAffinityDomain(xadPatient);
        } else {
            adCodes = new AffinityDomainCodes();
        }
    }

    private void checkAffinityDomain(String patID) throws XDSException {
        XADPatient pat;
        try {
            pat = new XADPatient(patID);
        } catch (Exception x) {
            throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, 
                    "Unknown patient ID: patientID:"+patID+" - "+x.getMessage(), null);
        }
        checkAffinityDomain(pat);
    }
    private void checkAffinityDomain(XADPatient pat) throws XDSException {
        if (cfg.isCheckAffinityDomain()) {
                if (!"ISO".equals(pat.getIssuerOfPatientID().getUniversalIdType())) {
                    throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, 
                            "PatientID with wrong UniversalId type (must be ISO)! pid:"+pat.getXADPatientID(), null);
                }
                String universalID = pat.getIssuerOfPatientID().getUniversalID();
                String[] ads = cfg.getAffinityDomain();
                for (int i = 0 ; i < ads.length ; i++) {
                    if (ads[i].equals(universalID)) {
                        return;
                    }
                }
                throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, 
                        "Unknown patient ID: Universial ID of "+pat.getXADPatientID()+" doesn't match a Affinity Domain!", null);
        }
    }

    private void checkMimetype(ExtrinsicObjectType obj, String[] mimes)
            throws XDSException {
        String mime = obj.getMimeType();
        boolean unknownMime = true;
        for (int j = 0; j < mimes.length ; j++) {
            if (mimes[j].equals(mime)) {
                unknownMime = false;
                break;
            }
        }
        if (unknownMime) {
            throw new XDSException(XDSException.XDS_ERR_REGISTRY_METADATA_ERROR, 
                    "Mimetype is not supported! mimetype:"+mime, null);
        }
    }

    private String getPatID(ExternalIdentifierType eiType) {
        if (XDSConstants.UUID_XDSDocumentEntry_patientId.equals(eiType.getIdentificationScheme()) ||
                XDSConstants.UUID_XDSFolder_patientId.equals(eiType.getIdentificationScheme()) ||
                XDSConstants.UUID_XDSSubmissionSet_patientId.equals(eiType.getIdentificationScheme())
                ) {
            return eiType.getValue() != null ? eiType.getValue() : "";
        }
        return null;
    }

    private List<Identifiable> store(SubmitObjectsRequest req, XDSPersistenceWrapper wrapper) throws XDSException {
        List<JAXBElement<? extends IdentifiableType>> objs = req.getRegistryObjectList().getIdentifiable();
        IdentifiableType obj;
        List<Identifiable> objects = new ArrayList<Identifiable>();
        List<IdentifiableType> postponed = new ArrayList<IdentifiableType>();

        // check and correct metadata
        wrapper.checkAndCorrectSubmitObjectsRequest(req);
        
        // first run - convert objects to persistent entities 
        for (int i=0,len=objs.size() ; i < len ; i++) {
            obj = objs.get(i).getValue();
            if (obj instanceof ExtrinsicObjectType) {
                objects.add(wrapper.toExtrinsicObject((ExtrinsicObjectType)obj));
            } else if (obj instanceof RegistryPackageType) {
                objects.add(wrapper.toRegistryPackage((RegistryPackageType)obj));
            } else if (obj instanceof AssociationType1) {
                if (!cfg.isCreateMissingCodes())
                    checkAssociationCodes((AssociationType1)obj, wrapper);
                objects.add(wrapper.toAssociation((AssociationType1)obj));
            } else if (obj instanceof ClassificationSchemeType) {//for initialization
                wrapper.toClassificationScheme((ClassificationSchemeType)obj, objects);
            } else if (obj instanceof ClassificationNodeType) {//for initialization
                wrapper.toClassificationNode((ClassificationNodeType)obj, null, objects);
            } else if (obj instanceof ObjectRefType) {//for initialization
                Identifiable tmp = getIdentifiableByUUID(obj.getId());
                log.debug("#### found RegistryObject for ObjectRef:"+tmp);
                if (tmp == null) {
                    log.debug("#### add ObjectRef: {}", obj.getId());
                    objects.add(wrapper.toObjectRef((ObjectRefType)obj));
                }
            } else { 
                // obj is an unknown type: no-op and show a warning in the log
                
                // find old id
                String oldId = null;
                for (Entry<String,String> e : wrapper.newUUIDs.entrySet()) {
                    if ( obj.getId().equals(e.getValue()))
                        oldId = e.getKey();
                }
                
                log.warn("Unknown RegistryObject provided for registering. id: {} ; old id: {}", obj.getId(), oldId );
            }

        }

        // second run - set references across objects
        for (JAXBElement<? extends IdentifiableType> elem : objs) {
            if (elem.getValue() instanceof AssociationType1) {
                wrapper.setAssociationSrcAndTarget((AssociationType1) elem.getValue());
            }
        }
        
        wrapper.logUIDMapping();
        storeRegistryObjects(objects);
        handleLifecycle(objects);
        return objects;
    }

    private void checkAssociationCodes(AssociationType1 assocType, XDSPersistenceWrapper wrapper) throws XDSException {
        List<ClassificationType> clList = assocType.getClassification();
        if (clList != null) {
            for (ClassificationType clType : clList) {
                if (wrapper.isXDSCode(clType)) {
                    String scheme = clType.getClassificationScheme();
                    String codeValue = clType.getNodeRepresentation();
                    String codeDesignator = clType.getSlot().get(0).getValueList().getValue().get(0);
                    if (!adCodes.isClassSchemeCodeDefined(scheme, codeValue, codeDesignator)) {
                        throw new XDSException(XDSException.XDS_ERR_REGISTRY_METADATA_ERROR, "Unknown code:"+
                                    codeValue+"^^"+codeDesignator+" ("+scheme+")", null);
                    }
                }
            }
        }
    }

    public RegistryObject getRegistryObjectByUUID(String id) {
        JPAQuery query = new JPAQuery(em);
        QRegistryObject registryObject = QRegistryObject.registryObject;
        RegistryObject obj = query.from(registryObject).where(registryObject.id.eq(id)).uniqueResult(registryObject);
        return obj;
    }
    public Identifiable getIdentifiableByUUID(String id) {
        JPAQuery query = new JPAQuery(em);
        QIdentifiable registryObject = QIdentifiable.identifiable;
        Identifiable obj = query.from(registryObject).where(registryObject.id.eq(id)).uniqueResult(registryObject);
        return obj;
    }
    /**
     * Standard query (ExtrinsicObject):
     *    SELECT i FROM ExtrinsicObject i LEFT JOIN FETCH i.slots as s LEFT JOIN FETCH i.classifications as c LEFT JOIN FETCH i.externalIdentifiers as e WHERE e.value = ?1 and e.identificationScheme.id='"
     * 
     * @param ids
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<XDSDocumentEntry> getDocumentEntriesByUUID(boolean fetch, String... ids) {
        //return getDocumentEntriesById(fetch, "id", ids);
        return (List<XDSDocumentEntry>) getObjectsByNamedQuery(XDSDocumentEntry.FIND_BY_UUIDS, XDSDocumentEntry.FIND_BY_UUIDS_NAME, ids);
    }
    @SuppressWarnings("unchecked")
    public List<XDSDocumentEntry> getDocumentEntriesByUniqueId(String... ids) {
        return (List<XDSDocumentEntry>) getObjectsByNamedQuery(XDSDocumentEntry.FIND_BY_UNIQUE_IDS, XDSDocumentEntry.FIND_BY_UNIQUE_IDS_NAME, ids);
    }

    public XDSDocumentEntry getDocumentEntryByUniqueId(String uniqueId) {
        return (XDSDocumentEntry) getObjectByNamedQuery(XDSDocumentEntry.FIND_BY_UNIQUE_ID, uniqueId);
    }
    public XDSDocumentEntry getDocumentEntryByUUID(String uuid) {
        return (XDSDocumentEntry) getObjectByNamedQuery(XDSDocumentEntry.FIND_BY_UUID, uuid);
    }

    public XDSDocumentEntry getDocumentEntryByUUIDandFetch(String uuid) {
        XDSDocumentEntry doc = (XDSDocumentEntry) getObjectByNamedQuery(XDSDocumentEntry.FIND_BY_UUID, uuid);
        return doc;
    }

    public XDSSubmissionSet getSubmissionSetByUniqueId(String uniqueId) {
        return (XDSSubmissionSet) getObjectByNamedQuery(XDSSubmissionSet.FIND_BY_UNIQUE_ID, uniqueId);
    }
    public XDSSubmissionSet getSubmissionSetByUUID(String uuid) {
        return (XDSSubmissionSet) getObjectByNamedQuery(XDSSubmissionSet.FIND_BY_UUID, uuid);
    }

    
    public XDSFolder getFolderByUniqueId(String uniqueId) {
        return (XDSFolder) getObjectByNamedQuery(XDSFolder.FIND_BY_UNIQUE_ID, uniqueId);
    }
    public XDSFolder getFolderByUUID(String uuid) {
        return (XDSFolder) getObjectByNamedQuery(XDSFolder.FIND_BY_UUID, uuid);
    }

    private Object getObjectByNamedQuery(String queryName, String param) {
        try {
            return em.createNamedQuery(queryName).setParameter(1, param).getSingleResult();
        } catch (NoResultException x) {
            log.warn("Cannot find an object using getObjectByNamedQuery: {}-{}", queryName, param);
            return null;
        }
    }
    @SuppressWarnings({ "rawtypes" })
    private List getObjectsByNamedQuery(String queryName, String paramName, String... params) {
        try {
            Query q = em.createNamedQuery(queryName);
            q.setParameter(paramName, Arrays.asList(params));
            return q.getResultList();
        } catch (NoResultException x) {
            log.warn("Cannot find an object using getObjectsByNamedQuery: {}", queryName);
            return null;
        }
    }

    public void storeRegistryObjects(List<Identifiable> objects) throws XDSException {
        log.debug("########## Store list of objects:{}", objects);
        // if the submitted list of objects is empty - throw an XDSException
        if (objects.isEmpty()) throw new XDSException(XDSException.XDS_ERR_REGISTRY_ERROR, 
                "The list of submitted objects for registering is empty", null);
        ArrayList<String> uids = new ArrayList<String>(objects.size());
        for (Identifiable i : objects) {
            if (i instanceof XDSObject) {
                String uid = ((XDSObject) i).getUniqueId();
                if (!uids.add(uid)) {
                    throw new XDSException(XDSException.XDS_ERR_REGISTRY_DUPLICATE_UNIQUE_ID_IN_MSG, "Duplicate uniqueId:"+uid, null);
                }
            }
        }
        Identifiable obj = null;
        try {
            for (int i = 0, len = objects.size() ; i < len ; i++) {
                obj = objects.get(i);
                log.debug("##### store {}   class:{}", obj.getId(), obj.getClass().getSimpleName());
                em.persist(obj);
                em.flush();
            }
        } catch (Exception x) {
            if ( (obj instanceof XDSFolder) || (obj instanceof XDSSubmissionSet)) {
                Throwable t = x;
                do {
                    if ("ConstraintViolationException".equals(t.getClass().getSimpleName())) {
                        throw new XDSException(XDSException.XDS_ERR_DUPLICATE_UNIQUE_ID_IN_REGISTRY, 
                            "UniqueId '"+((XDSObject) obj).getUniqueId()+"' of "+obj.getClass().getSimpleName()+" already exist! (or entryUUID:"+obj.getId()+")", x);
                    }
                    t = t.getCause();
                } while (t != null);
            }
            throw new XDSException(XDSException.XDS_ERR_REGISTRY_ERROR, 
                "Failed to persist object! :"+obj, x);
        }
    }

    public XADPatient getPatient(String pid, boolean createMissing) throws XDSException {
        XADPatient qryPat = new XADPatient(pid);
        if (!"ISO".equals(qryPat.getIssuerOfPatientID().getUniversalIdType())) {
            throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, "PatientID with wrong UniversalId type (must be ISO)! pid:"+pid, null);
        }
        XADPatient pat;
        // use a nested separate transaction to handle concurrent patient creation,
        // retry one time (there will be an exception if the unique constraint was violated)
        try {
            pat = self.getPatientSeparateTransaction(qryPat, createMissing);
        } catch (EJBException e) {
            log.warn("Failed to retrieve a patient. Re-trying due to a possible race condition. Patient id: {}", qryPat.getPatientID());

            // most probably a concurrent register operation attempted to create patient with same id, try one more time
            pat = self.getPatientSeparateTransaction(qryPat, createMissing);

            log.info("Succeeded with a retry of retrieving a patient. Patient id: "+qryPat.getPatientID());
        }
        return em.merge(pat);
    }
    
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public XADPatient getPatientSeparateTransaction(XADPatient qryPat, boolean createMissing) throws XDSException {
        try {
            XADPatient pat = (XADPatient) em.createNamedQuery(XADPatient.FIND_PATIENT_BY_PID_AND_UNIVERSAL_ID)
                .setParameter(1, qryPat.getPatientID())
                .setParameter(2, qryPat.getIssuerOfPatientID().getUniversalID())
                .getSingleResult();
            if (pat.getLinkedPatient() != null) {
                throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, "PatientID "+pat+" is merged with "+pat.getLinkedPatient(), null);
            }
            return pat;
        } catch (NoResultException x) {
            if (createMissing) {
                log.warn("Unknown XAD Patient! Create missing Patient with pid: "+qryPat.getPatientID());
                qryPat.setIssuerOfPatientID(getOrCreateIssuerOfPID(qryPat.getIssuerOfPatientID()));
                em.persist(qryPat);
                return qryPat;
            } else {
                throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, "PatientID:"+qryPat.getPatientID(), null);
            }
        }
    }

    // TODO: use nested transaction to protect from concurrent adding here as well? Or is it very unlikely here?
    
    public boolean newPatientID(String pid) throws XDSException {
        checkAffinityDomain(pid);
        XADPatient qryPat = new XADPatient(pid);
        try {
            em.createNamedQuery(XADPatient.FIND_PATIENT_BY_PID_AND_UNIVERSAL_ID)
                .setParameter(1, qryPat.getPatientID())
                .setParameter(2, qryPat.getIssuerOfPatientID().getUniversalID())
                .getSingleResult();
            return false;
        } catch (NoResultException x) {
            qryPat.setIssuerOfPatientID(getOrCreateIssuerOfPID(qryPat.getIssuerOfPatientID()));
            em.persist(qryPat);
            return true;
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<String> listPatientIDs(String affinityDomain) {
        List<XADPatient> pats = (List<XADPatient>) em.createNamedQuery(XADPatient.FIND_PATIENT_BY_AFFINITYDOMAIN)
        .setParameter(1, affinityDomain.replace('*', '%'))
        .getResultList();
        List<String> patIDs = new ArrayList<String>(pats.size());
        for (int i = 0, len = pats.size() ; i < len ; i++) {
            patIDs.add(pats.get(i).getXADPatientID());
        }
        return patIDs;
    }
    
    private XADIssuer getOrCreateIssuerOfPID(XADIssuer issuer) {
        try {
            return (XADIssuer) em.createNamedQuery(XADIssuer.FIND_ISSUER_BY_UID)
            .setParameter(1, issuer.getUniversalID()).getSingleResult();
        } catch (NoResultException x) {
            log.debug("Create new Issuer of PatientID:"+issuer);
            em.persist(issuer);
            return issuer;
        }            
    }

    public void linkPatient(String pid, String newPID) throws XDSException {
        XADPatient qryPat = new XADPatient(pid);
        XADPatient pat;
        try {
            pat = (XADPatient) em.createNamedQuery(XADPatient.FIND_PATIENT_BY_PID_AND_UNIVERSAL_ID)
                .setParameter(1, qryPat.getPatientID())
                .setParameter(2, qryPat.getIssuerOfPatientID().getUniversalID())
                .getSingleResult();
        } catch (NoResultException x) {
            throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, "PatientID:"+pid, null);
        }
        XADPatient newPat = null;
        if (newPID != null) {
            try {
                qryPat = new XADPatient(newPID);
                newPat = (XADPatient) em.createNamedQuery(XADPatient.FIND_PATIENT_BY_PID_AND_UNIVERSAL_ID)
                    .setParameter(1, qryPat.getPatientID())
                    .setParameter(2, qryPat.getIssuerOfPatientID().getUniversalID())
                    .getSingleResult();
            } catch (NoResultException x) {
                throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, "PatientID:"+newPID, null);
            }
        }
        pat.setLinkedPatient(newPat);
   }
    public boolean mergePatient(String subsumedPID, String survivingPID) throws XDSException {
    	if (subsumedPID.equals(survivingPID)) {
    		log.info("Patient Merge ignored! subsumed and surviving PIDs are equal!");
    		return false;
    	}
    	//TODO check AffinitDomain
    	
        XADPatient qryPat = new XADPatient(subsumedPID);
        XADPatient subsumedPat;
        try {
            subsumedPat = (XADPatient) em.createNamedQuery(XADPatient.FIND_PATIENT_BY_PID_AND_UNIVERSAL_ID)
                .setParameter(1, qryPat.getPatientID())
                .setParameter(2, qryPat.getIssuerOfPatientID().getUniversalID())
                .getSingleResult();
        } catch (NoResultException x) {
            throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, "Subsumed PatientID:"+subsumedPID, null);
        }
        if (subsumedPat.getLinkedPatient() != null) {
        	throw new XDSException(XDSException.XDS_ERR_REGISTRY_ERROR, "Subsumed PatientID already linked!:"+
        			subsumedPID+" linked to "+subsumedPat.getLinkedPatient().getCXPatientID(), null);
        }
        XADPatient survivingPat = null;
        if (survivingPID != null) {
            try {
                qryPat = new XADPatient(survivingPID);
                survivingPat = (XADPatient) em.createNamedQuery(XADPatient.FIND_PATIENT_BY_PID_AND_UNIVERSAL_ID)
                    .setParameter(1, qryPat.getPatientID())
                    .setParameter(2, qryPat.getIssuerOfPatientID().getUniversalID())
                    .getSingleResult();
            } catch (NoResultException x) {
                throw new XDSException(XDSException.XDS_ERR_UNKNOWN_PATID, "Surviving PatientID:"+survivingPID, null);
            }
        }
        if (survivingPat.getLinkedPatient() != null) {
        	throw new XDSException(XDSException.XDS_ERR_REGISTRY_ERROR, "Suviving PatientID already linked!:"+
        			survivingPID+" linked to "+survivingPat.getLinkedPatient().getCXPatientID(), null);
        }
        mergePatient("XDSSubmissionSet", subsumedPat, survivingPat);  
        mergePatient("XDSDocumentEntry", subsumedPat, survivingPat);  
        mergePatient("XDSFolder", subsumedPat, survivingPat);  
        subsumedPat.setLinkedPatient(survivingPat);
        return true;
   }

   private void mergePatient(String tableName, XADPatient subsumedPat,
			XADPatient survivingPat) {
        Query q = em.createQuery("SELECT s FROM " + tableName + " s WHERE s.patient = :subsumed");
        q.setParameter("subsumed", subsumedPat);
        @SuppressWarnings("unchecked")
        List<RegistryObject> list = q.getResultList();
        String idScheme;
        for (RegistryObject ro : list) {
            for (ExternalIdentifierType ei : ro.getExternalIdentifiers()) {
                idScheme = ei.getIdentificationScheme();
                if ( XDSConstants.UUID_XDSDocumentEntry_patientId.equals(idScheme) ||
                        XDSConstants.UUID_XDSFolder_patientId.equals(idScheme) ||
                        XDSConstants.UUID_XDSSubmissionSet_patientId.equals(idScheme) ) {
                    ei.setValue(survivingPat.getXADPatientID());
                }
            };
            em.merge(ro);
        }
        Query qUpd = em.createQuery("UPDATE "+tableName+" s SET s.patient = :survived WHERE s.patient = :subsumed");  
        qUpd.setParameter("survived", survivingPat);
        qUpd.setParameter("subsumed", subsumedPat);
        qUpd.executeUpdate();
        
   }
 
    public XDSCode getXDSCode(ClassificationType clType, boolean createMissing) throws XDSException {
        String scheme = clType.getClassificationScheme();
        String codeValue = clType.getNodeRepresentation();
        String codeDesignator = clType.getSlot().get(0).getValueList().getValue().get(0);
        if (!createMissing) {
            if (!adCodes.isClassSchemeCodeDefined(scheme, codeValue, codeDesignator)) {
                throw new XDSException(XDSException.XDS_ERR_REGISTRY_METADATA_ERROR, "Unknown code:"+
                            codeValue+"^^"+codeDesignator+" ("+scheme+")", null);
            }
        }
        XDSCode code = new XDSCode(codeValue, codeDesignator,
                clType.getName().getLocalizedString().get(0).getValue(),
                scheme);

        try {
            return (XDSCode) em.createNamedQuery(XDSCode.FIND_BY_CODE_VALUE)
                .setParameter(1, codeValue)
                .setParameter(2, codeDesignator)
                .setParameter(3, scheme)
                .getSingleResult();
        } catch (NoResultException x) {
            log.debug("Store new XDS Code! code:"+code);
            em.persist(code);
            return code;
        }
    }
    
    public void handleLifecycle(List<Identifiable> objs) throws XDSException {
        log.debug("########## Handle lifecycle");
        Identifiable obj;
        Association assoc;
        for (int i=0,len=objs.size() ; i < len ; i++) {
            obj = objs.get(i);
            if (obj instanceof Association) {
                assoc = (Association) obj;
                if (assoc.getAssocType() == null) {
                    throw new XDSException(XDSException.XDS_ERR_REGISTRY_METADATA_ERROR, "Missing AssociationType! Association:"+assoc.getId(), null);
                }
                log.debug("### handle Association:"+assoc.getId()+" assocType:"+assoc.getAssocType().getId());
                if (XDSConstants.RPLC.equals(assoc.getAssocType().getId())) {
                    handleReplace(assoc);
                } else if (XDSConstants.XFRM_RPLC.equals(assoc.getAssocType().getId())) {
                    handleReplace(assoc);
                } else if (XDSConstants.APND.equals(assoc.getAssocType().getId())) {
                    getTargetAndcheckDeprecated(assoc);
                } else if (XDSConstants.XFRM.equals(assoc.getAssocType().getId())) {
                    getTargetAndcheckDeprecated(assoc);
                } else if (XDSConstants.HAS_MEMBER.equals(assoc.getAssocType().getId())) {
                    handleHasMember(assoc);
                }

            }
        }
        log.debug("########## handle lifecycle finished! ");
    }

    private void handleReplace(Association assoc) throws XDSException {
        log.debug("##### Handle replace of document: source: {}  target:{}(status:{})",
                new Object[]{assoc.getSourceObject().getId(), assoc.getTargetObject().getId(),
                    assoc.getTargetObject().getStatus()} );
        RegistryObject ro = getTargetAndcheckDeprecated(assoc);
        ro.setStatus(XDSConstants.STATUS_DEPRECATED);
        em.merge(ro);
        deprecateAPNDandXFRM(ro);
       
        @SuppressWarnings("unchecked")
        List<Association> folderAssocs = (List<Association>) em.createQuery(
                "SELECT a FROM Association a WHERE ( a.assocType.id = '"+XDSConstants.HAS_MEMBER+
                "') AND a.targetObject=?1")
                .setParameter(1, ro).getResultList();
        for(Association fAssoc : folderAssocs) {
            if (fAssoc.getSourceObject() instanceof XDSFolder) {
                updateLastUpdateTime(fAssoc.getSourceObject());
                Association newAssoc = new Association();
                newAssoc.setId("urn:uuid:"+UUID.randomUUID().toString());
                newAssoc.setSourceObject(fAssoc.getSourceObject());//folder
                newAssoc.setTargetObject(assoc.getSourceObject());//new doc
                newAssoc.setAssocType(fAssoc.getAssocType());
                em.persist(newAssoc);
            }
        }
    }
    public void deprecateAPNDandXFRM(RegistryObject ro) {
        deprecateAPNDandXFRM(ro, em.createQuery(
                "SELECT a FROM Association a WHERE ( a.assocType.id = '"+XDSConstants.APND+
                "' OR a.assocType.id = '"+XDSConstants.XFRM+"') AND a.targetObject=?1"));
    }
    private void deprecateAPNDandXFRM(RegistryObject ro, Query query) {
        @SuppressWarnings("unchecked")
        List<Association> assocs = (List<Association>) query
            .setParameter(1, ro).getResultList();
        RegistryObject ro1;
        for (int i = 0, len=assocs.size(); i < len ; i++) {
            ro1 = assocs.get(i).getSourceObject();
            ro1.setStatus(XDSConstants.STATUS_DEPRECATED);
            log.debug("Deprecate object "+ro1.getId()+" which is a '"+assocs.get(i).getAssocType().getId()+"' of "+ro.getId());
            em.merge(ro1);
            deprecateAPNDandXFRM(ro1, query);
        }
    }

    private RegistryObject getTargetAndcheckDeprecated(Association assoc) throws XDSException {
        RegistryObject ro = assoc.getTargetObject();
        if (XDSConstants.STATUS_DEPRECATED.equals(ro.getStatus())) {
            throw new XDSException(XDSException.XDS_ERR_REGISTRY_DEPRECATED_DOC_ERROR, 
                    "Target RegistryObject is deprecated! Association:"+assoc, null);
        }
        return ro;
    }

    private void handleHasMember(Association assoc) {
        log.debug("########## HANDLE HasMember! source:"+assoc.getSourceObject());
        if(assoc.getSourceObject() instanceof XDSFolder) {
            log.debug("########## HANDLE HasMember! updateLastUpdateTime");
            updateLastUpdateTime(assoc.getSourceObject());
        }
        log.debug("########## HANDLE HasMember finished!");
    }

    public void updateLastUpdateTime(RegistryObject ro) {
        log.debug("########## updateLastUpdateTime!");
        List<Slot> slots = ro.getSlots();
        log.debug("########## updateLastUpdateTime! slots:{}",slots);
        for (Slot slot : slots) {
            log.debug("########## updateLastUpdateTime! slot Name:{}",slot.getName());
            if (XDSConstants.SLOT_NAME_LAST_UPDATE_TIME.equals(slot.getName())) {
                log.debug("########## updateLastUpdateTime! slot setValue");
                slot.setValue(new SimpleDateFormat("yyyyMMddHHmmss.SSS").format(new Date()));
                log.debug("########## updateLastUpdateTime! persist:");
                em.merge(slot);
                log.debug("########## updateLastUpdateTime! persist done:{}",slot.getName());
            }
        }
        log.debug("########## updateLastUpdateTime! done");
    }
    
    public EntityManager getEntityManager() {
        return em;
    }

    /**
     * NOT PROPERLY SUPPORTED! FOR NOW, THIS IS ONLY USED LOCALLY BY THE BROWSER. <br/>
     * 
     * Deletes identifiables from the registry according to the IHE "metadata delete" specification. 
       <br/><br/>   
      
      <a href='http://www.ihe.net/uploadedFiles/Documents/ITI/IHE_ITI_Suppl_XDS_Metadata_Update.pdf'> Spec</a>
      3.62.4.1.2 Message Semantics 
        The Document Administrator actor is responsible to discover the entryUUID attribute value of 
        the objects that are to be deleted through the Registry Stored Query [ITI-18] transaction or 
        known history of the metadata. The entryUUID attribute values are packaged as an ObjectRef 
        list in the request message. <br/><br/>
        
        Since a DocumentEntry always has at least a HasMember association to its SubmissionSet and 
        an object cannot be deleted while association objects still reference it, to delete the 
        DocumentEntry, the minimal deletion request is for the DocumentEntry and Association objects. 
        The ebRS 3.0 RemoveObjectsRequest message shall be used. The following restrictions shall be 
        followed:<br/><br/>
         
        1. The AdhocQuery parameter shall not be specified. Deletions are specified only by the 
        ObjectRefList. <br/>
        2. The deletionScope parameter shall not be specified.
     */
    @SuppressWarnings("unchecked")
    @Override
    public RegistryResponseType deleteObjects(RemoveObjectsRequest removeReq) {

        // extract ids into a string list
       List<ObjectRefType> list = removeReq.getObjectRefList().getObjectRef();
       List<String> uuids = new ArrayList<String>();
       
       for (ObjectRefType objRef : list) {
           uuids.add(objRef.getId());
       }
       
       // Deletion. The JPA DELETE query does not handle cascading, so we  
       // get objects manually and remove them one by one for now.

       log.info("Deleting objects with uuids {}",uuids);

        if (uuids.isEmpty()) return null;

       List<RegistryObject> objs = em.createQuery("SELECT r FROM RegistryObject r WHERE r.id IN (:uuids)").setParameter("uuids", uuids).getResultList();
       
       for (RegistryObject obj : objs)
           em.remove(obj);
       
       
       return null;
       
    }

    /**
     * Async to make sure we don't end up having recursive PostConstruct calls for XdsServiceImpl
     */
    @Override
    @Asynchronous
    public void checkAndAutoInitializeRegistry()  {

        // make sure we don't let more than 1 init run at the same time
        synchronized (XDSRegistryBean.class) {
            log.info("Checking if the XDS registry needs to be initialized with metadata (XDS Registry auto-initialization)...");
            // if there are no identifiables in the database, perform the XDS initialization
            Long identifiablesTotal = (Long) em.createQuery("SELECT count(i) FROM Identifiable i").getResultList().get(0);
            if (identifiablesTotal == 0) {
                log.info("Initializing XDS Registry with default metadata...");
                XDSInitCommon.autoInitializeRegistry(this);
                log.info("XDS Registry successfully initialized");
            } else {
                log.info("XDS Registry already contains data - no initialization needed");
            }
        }

    }
    

}

