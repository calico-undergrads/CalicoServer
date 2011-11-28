package calico.admin.requesthandlers;


import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.http.*;
import org.apache.http.entity.*;
import org.apache.http.protocol.*;
import org.apache.http.util.*;

import org.json.me.*;

import calico.*;
import calico.admin.*;
import calico.admin.exceptions.*;
import calico.clients.*;
import calico.controllers.CCanvasController;
import calico.controllers.CStrokeController;

public class StrokeListRequestHandler extends AdminBasicRequestHandler
{
	
	//  /session/strokes/get/<uuid>
	
	protected void handleRequest(final HttpRequest request, final HttpResponse response) throws HttpException, IOException, JSONException, CalicoAPIErrorException
	{

		Properties props = new Properties();
		props.setProperty("strokes", Arrays.toString(CStrokeController.strokes.keySet().toLongArray()));
		
		throw new SuccessException(props);
		
		
	}
	
	
}