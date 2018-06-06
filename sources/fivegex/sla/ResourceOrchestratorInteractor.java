
package fivegex.sla;

import cc.clayman.logging.BitMask;
import cc.clayman.logging.Logger;
import cc.clayman.logging.MASK;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
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
    
    
    private final String gvnfmAddress;
    private final int gvnfmPort;
    private final String gvnfmURL;
    private final String gvnfmEndpoint = "edit-config?blocking";
    
    
    private final String infraURI;
    private final String gvnfmURI;
    XMLResource infraView;
    Document infraViewDoc;
    
    Resty rest;
    
    Logger logger;
    
    
    public ResourceOrchestratorInteractor(String orchestratorAddress, 
                                          int orchestratorPort, 
                                          String orchestratorURL,
                                          String gvnfmAddress,
                                          int gvnfmPort,
                                          String gvnfmURL) {
        
        logger = Logger.getLogger("log");
        logger.addOutput(System.err, new BitMask(MASK.ERROR));
        logger.addOutput(System.out, new BitMask(MASK.STDOUT));
        
        this.orchestratorAddress = orchestratorAddress;
        this.orchestratorPort = orchestratorPort;
        this.orchestratorURL = orchestratorURL;
        
        this.gvnfmAddress = gvnfmAddress;
        this.gvnfmPort = gvnfmPort;
        this.gvnfmURL = gvnfmURL;
        
        this.infraURI = "http://" + this.orchestratorAddress + ":" + this.orchestratorPort + this.orchestratorURL + orchestratorEndpoint;
        this.gvnfmURI = "http://" + this.gvnfmAddress + ":" + this.gvnfmPort + this.gvnfmURL + gvnfmEndpoint;
        rest = new Resty();
        
    }
    
    
    public void getInfrastructureView() throws IOException {
        infraView = rest.xml(infraURI);
    }
    
    
    public void getInfrastructureView(int timeout) throws IOException {
        boolean retrieved = false;
        long t1, t2;
        t1 = System.currentTimeMillis();
        t2 = t1;
        
        while ((t2 - t1)/1000 < timeout && !retrieved) {
            try {
                infraView = rest.xml(infraURI);
                retrieved = true;
                
            } catch (Exception e) {
                logger.logln(MASK.STDOUT, leadin() + "Error while getting infrastructure view from RO");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {}
                
                t2 = System.currentTimeMillis();
            }
        }
        
        if (!retrieved)
            throw new IOException("Could not retrieve infrastructure view from RO");
    }
    
    
    public String getNFtype(String vnfId) throws IOException {
        String nfId;
        boolean found = false;
        String nfType = "";
           
        infraViewDoc = infraView.doc();
        infraViewDoc.getDocumentElement().normalize();

        NodeList nfInstances = infraViewDoc.getElementsByTagName("NF_instances");
        Node nfs = nfInstances.item(0);
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
            throw new IOException("NF " + vnfId + " not found in the current infrastructure");
            
        }
        
        return nfType;

    }
    
    
    public void setMigrationStatusOnNF(String vnfId) throws IOException {
        String nfId;
        boolean found = false;
           
        infraViewDoc = infraView.doc();
        infraViewDoc.getDocumentElement().normalize();

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
            throw new IOException("NF " + vnfId + " not found in the current infrastructure");

    }
    
    // this might not be tested yet depending on the development status of ESCAPE and GVNFM
    public JSONObject startMigration() {
        JSONObject response = new JSONObject();
        
        try { 
            XMLResource xml = rest.xml(gvnfmURI, content(getRequestBodyAsBytes(infraViewDoc)));
            //getNiceLyFormattedXMLDocument(xml.doc());
            
            // Might include additional info from above reply (once agreed on format)
            response.put("success", true);
        } catch (IOException | JSONException e) {
            logger.logln(MASK.ERROR, leadin() + "Cannot update infrastructure view on Slicer/ESCAPE" + e.getMessage());
            //e.printStackTrace();
        }
        return response;
        
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
        String escapeHost = "clayone.ee.ucl.ac.uk";
        int escapePort = 8888;
        String escapeURL = "/escape/orchestration/";
        String gvnfmURL = "/escape/orchestration/";
        
        
        try {
            ResourceOrchestratorInteractor g = new ResourceOrchestratorInteractor(escapeHost, escapePort, escapeURL, escapeHost, escapePort, gvnfmURL);
            g.getInfrastructureView();
            g.getNFtype("host1");
            g.setMigrationStatusOnNF("host1");
            //g.startMigration();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    
}
