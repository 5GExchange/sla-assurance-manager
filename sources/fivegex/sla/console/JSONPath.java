package fivegex.sla.console;

import java.io.IOException;
import java.io.StringReader;

import us.monoid.web.*;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.jp.javacc.JSONPathCompiler;
import us.monoid.web.jp.javacc.ParseException;

public class JSONPath {
    private JSONPathCompiler compiler;
    private String query;
    
    public JSONPath(String query) {
        this.query = query;
    }

    public Object match(JSONObject json) throws JSONException, ParseException, IOException {
        Object result = getCompiler().json().eval(json);
        return result;
    }


    protected JSONPathCompiler getCompiler() throws ParseException {
        if (null == compiler) {
            compiler = new JSONPathCompiler(new StringReader(query));
        }
        return compiler;
    }

}
