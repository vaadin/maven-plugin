package org.codehaus.mojo.gwt.servlets;

import java.util.Collection;
import java.util.List;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

@RemoteServiceRelativePath(value = "/HelloService")
public interface HelloRemoteService extends RemoteService {

    Collection<Integer> returnsGenerics(List<String> values);

    int returnsPrimitive(String[] values);

    void returnsVoid(String value);

    String[] returnsArray(String[] values);
}
