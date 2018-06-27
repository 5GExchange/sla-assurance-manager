
package fivegex.sla;

import cc.clayman.logging.BitMask;
import cc.clayman.logging.Logger;
import cc.clayman.logging.MASK;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.Resty;
import us.monoid.web.XMLResource;
import static us.monoid.web.Resty.content;


public class ResourceOrchestratorInteractor {
    private final String orchestratorAddress;
    private final int orchestratorPort;
    private final String orchestratorURL;
    private final String orchestratorEndpoint = "get-config?blocking";
    private final String orchestratorMappingEndpoint = "mappings";
    private final String gVNFMEndpoint = "edit-config?blocking";
    
    private final String infraURI;
    private final String mappingURI;
    private final String gVNFMURI;
    
    XMLResource infraView;
    Document infraViewDoc;
    
    Resty rest;
    
    Logger logger;
    
    
    public ResourceOrchestratorInteractor(String orchestratorAddress, 
                                          int orchestratorPort, 
                                          String orchestratorURL) {
        
        logger = Logger.getLogger("log");
        logger.addOutput(System.err, new BitMask(MASK.ERROR));
        logger.addOutput(System.out, new BitMask(MASK.STDOUT));
        
        this.orchestratorAddress = orchestratorAddress;
        this.orchestratorPort = orchestratorPort;
        this.orchestratorURL = orchestratorURL;
        
        this.infraURI = "http://" + this.orchestratorAddress + ":" + this.orchestratorPort + this.orchestratorURL + orchestratorEndpoint;
        this.mappingURI = "http://" + this.orchestratorAddress + ":" + this.orchestratorPort + this.orchestratorURL + orchestratorMappingEndpoint;
        this.gVNFMURI = "http://" + this.orchestratorAddress + ":" + this.orchestratorPort + this.orchestratorURL + gVNFMEndpoint;
        
        rest = new Resty();
        
    }
    
    
    public void getInfrastructureView() throws ResourceOrchestratorException {
        try {
            infraView = rest.xml(infraURI);
        } catch (IOException ioe) {
            throw new ResourceOrchestratorException("Error while getting infrastructure view from the Resource Orchestrator: " + ioe.getMessage());
        }
    }
    
    
    public void getInfrastructureView(int timeout) throws ResourceOrchestratorException {
        boolean retrieved = false;
        long t1, t2;
        t1 = System.currentTimeMillis();
        t2 = t1;
        
        while ((t2 - t1)/1000 < timeout && !retrieved) {
            try {
                infraView = rest.xml(infraURI);
                retrieved = true;
                
            } catch (IOException e) {
                logger.logln(MASK.STDOUT, leadin() + "Error while getting infrastructure view from the Resource Orchestrator: " + e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {}
                
                t2 = System.currentTimeMillis();
            }
        }
        
        if (!retrieved)
            throw new ResourceOrchestratorException("Could not retrieve infrastructure view from the Resource Orchestrator");
    }
    
    
    public String getNFtype(String vnfId) throws ResourceOrchestratorException {
        String nfId;
        boolean found = false;
        String nfType = "";
        
        try { 
            infraViewDoc = infraView.doc();
            infraViewDoc.getDocumentElement().normalize();
        } catch (IOException ioe) {
            throw new ResourceOrchestratorException("Error while processing Infrastructure Virtualizer: " + ioe.getMessage());
        }
        
        NodeList nfInstances = infraViewDoc.getElementsByTagName("NF_instances");
        Node nfs = nfInstances.item(0);
        if (nfs == null)
            throw new ResourceOrchestratorException("There are no NFs in the current infrastructure");
        
        NodeList nodes = nfs.getChildNodes();

        for (int i = 0; i < nodes.getLength() && !found; i++) {
            Node nf = nodes.item(i);
            if (nf.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nf; 
                nfId = eElement.getElementsByTagName("id").item(0).getTextContent();

                if (nfId.equals(vnfId)) {
                    nfType = eElement.getElementsByTagName("type").item(0).getTextContent();
                    found = true;
                    logger.logln(MASK.STDOUT, leadin() + "Type "  + nfType + " found for NF " + vnfId);
                }
            } 
        }

        if (!found) {
            throw new ResourceOrchestratorException("NF " + vnfId + " not found in the current infrastructure");
            
        }
        
        return nfType;

    }
    
    
    public void setMigrationStatusOnNF(String vnfId) throws ResourceOrchestratorException {
        String nfId;
        boolean found = false;
           
        try { 
            infraViewDoc = infraView.doc();
            infraViewDoc.getDocumentElement().normalize();
        } catch (IOException ioe) {
            throw new ResourceOrchestratorException("Error while processing Infrastructure Virtualizer: " + ioe.getMessage());
        }

        NodeList nfInstances = infraViewDoc.getElementsByTagName("NF_instances");
        Node nfs = nfInstances.item(0);
        NodeList nodes = nfs.getChildNodes();

        for (int i = 0; i < nodes.getLength() && !found; i++) {
            Node nf = nodes.item(i);
            if (nf.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nf; 
                nfId = eElement.getElementsByTagName("id").item(0).getTextContent();

                if (nfId.equals(vnfId)) {
                    Node migrate = infraViewDoc.createElement("status");
                    migrate.appendChild(infraViewDoc.createTextNode("migrate"));
                    eElement.appendChild(migrate);
                    found = true;
                    logger.logln(MASK.STDOUT, leadin() + "Migration flag set correctly for NF " + vnfId);
                }
            } 
        }

        if (!found)
            throw new ResourceOrchestratorException("NF " + vnfId + " not found in the current infrastructure");

    }
    
    
    public JSONObject startMigration(String vnfID) throws ResourceOrchestratorException {
        JSONObject response = new JSONObject();
        
        try {
            // invoking mapping before performing migration and getting the lower level VNF ID
            Document mappingBody = generateMappingBody(vnfID);
            String lowerVNFid = getMappingInfo(mappingBody);
            if (lowerVNFid.equals("NOT_FOUND"))
                throw new ResourceOrchestratorException("Error: VNF => " + vnfID + " does not exist in the infrastructure");
            
            XMLResource xml = rest.xml(this.gVNFMURI, content(getRequestBodyAsBytes(infraViewDoc)));
            
            String lowerMigratedVNFid = getMappingInfo(mappingBody);
            
            if (lowerMigratedVNFid.equals(lowerVNFid)) 
                response.put("success", false);
            else {
                response.put("success", true);
                response.put("id", lowerMigratedVNFid);
            }
            
            return response;
            
        } catch (IOException | JSONException e) {
            throw new ResourceOrchestratorException("Migration failed: cannot update infrastructure view on the Resource Orchestrator. " + e.getMessage());
          }
    }
    
    
    private Document generateMappingBody(String vnfId) throws IOException {
        String sliceId = this.orchestratorURL.split("/")[2];

        Document doc=null;
        
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            
            Element mappings = doc.createElement("mappings");
            doc.appendChild(mappings);

            Element mapping = doc.createElement("mapping");
            mappings.appendChild(mapping);

            Element obj = doc.createElement("object");
            mapping.appendChild(obj);
            
            obj.insertBefore(doc.createTextNode("/virtualizer[id=" + sliceId + "]/nodes/node[id=SingleBiSBiS]/NF_instances/node[id=" + vnfId + "]"), 
                                                obj.getLastChild());
        }
            
        catch (ParserConfigurationException | DOMException | IllegalArgumentException e) {
               throw new IOException("Error while generating XML request body" + e.getMessage());
        }
  
        return doc;
    }
    
    
    
    private String getMappingInfo(Document mappingBody) throws IOException {
        String lowerVNFid=null;
        XMLResource mappingInfo;
        
        try {
            mappingInfo = rest.xml(this.mappingURI, content(getRequestBodyAsBytes(mappingBody)));
        } catch (IOException ioe) {
            throw ioe;
        }
        
        Document doc = mappingInfo.doc();
        doc.getDocumentElement().normalize();
        
        NodeList mappings = doc.getElementsByTagName("mapping");
        Node mapping = mappings.item(0);
        if (mapping.getNodeType() == Node.ELEMENT_NODE) {
            Element eElement = (Element) mapping;
            NodeList target = eElement.getElementsByTagName("target");
            Node targetElement = target.item(0);
            if (targetElement.getNodeType() == Node.ELEMENT_NODE) {
                Element tElement = (Element) targetElement;
                lowerVNFid = tElement.getElementsByTagName("object").item(0).getTextContent();
                
                Pattern p = Pattern.compile("id=([^\\]]*)");
                Matcher m = p.matcher(lowerVNFid);
                
                if (m.find()) {
                    m.find();
                    lowerVNFid = m.group(1);
                }
            }
        }
        return lowerVNFid;
    }
    
    
    
    private byte[] getRequestBodyAsBytes(Document doc) throws IOException {
        byte [] array;
        
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            ByteArrayOutputStream bos=new ByteArrayOutputStream();
            StreamResult result=new StreamResult(bos);
            transformer.transform(source, result);
            array = bos.toByteArray();
        } catch (TransformerException e) {
            throw new IOException("Error while converting XML document to byte array" + e.getMessage());
        }
        return array;
    }  

    protected String leadin() {
        return "ResourceOrchestratorInteractor: ";
    }

    public static String getNiceLyFormattedXMLDocument(Document doc) {
        String result="";
        
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            Writer stringWriter = new StringWriter();
            StreamResult streamResult = new StreamResult(stringWriter);
            transformer.transform(new DOMSource(doc), streamResult);
            result = stringWriter.toString();
        } catch (TransformerException e) {
            
        }
 
        return result;
    }
    
    
    
    public static void main(String[] args) {
        String escapeHost = "tusa.ee.ucl.ac.uk";
        int escapePort = 8888;
        String escapeURL = "/ro/v0/";
        
        try {
            ResourceOrchestratorInteractor g = new ResourceOrchestratorInteractor(escapeHost, escapePort, escapeURL);
            //g.getInfrastructureView();
            //g.getNFtype("host1");
            //g.setMigrationStatusOnNF("host1");
            //g.startMigration();
            Document mappingBody = g.generateMappingBody("host003id");
            String lowerVNFid = g.getMappingInfo(mappingBody);
            System.out.println(lowerVNFid);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    
}
