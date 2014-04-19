/**
 *  (C) 2013-2014 Stephan Rauh http://www.beyondjava.net
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.beyondjava.angularFaces.secure;

import java.util.*;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.*;

import de.beyondjava.angularFaces.common.*;

/**
 * Default implementation of an AngularFaces security phase listener. Default implementation of a security filter to add
 * a limited (but not sufficient) level of security to your application. Note that you are still responsible for your
 * applications security. Using AngularFaces may help you to secure your application, but it\"s not enough. AngularFaces
 * and it\"s author do not take any responsibilty for any security breach or any other damage occuring using
 * AngularFaces. Use at own risk.
 *
 * @author Stephan Rauh http://www.beyondjava.net
 *
 */

public class NGSecurityPhaseListenerImpl implements PhaseListener, NGSecurityPhaseListener {

    private static final long serialVersionUID = -3898083464626663562L;

    @Override
    public void afterPhase(PhaseEvent pe) {
    }

    @Override
    public void beforePhase(PhaseEvent pe) {
        Map<String, String> parameterMap = pe.getFacesContext().getExternalContext().getRequestParameterMap();
        lookForUnexpectedParameters(parameterMap, pe.getFacesContext());
        examineParameters(parameterMap, pe.getFacesContext());
    }

    public void collectParameterList(UIComponent component, List<String> parameters,
            Map<String, NGBeanAttributeInfo> infos) {
        if (null != component) {
            parameters.add(component.getClientId());
            if (component.getClass().getName()
                    .equals("org.primefaces.component.selectbooleancheckbox.SelectBooleanCheckbox")) {
                parameters.add(component.getClientId() + "_input");
            }
            if (component.getClass().getName().equals("org.primefaces.component.selectonemenu.SelectOneMenu")) {
                parameters.add(component.getClientId() + "_input");
                parameters.add(component.getClientId() + "_focus");
            }
            NGBeanAttributeInfo info = ELTools.getBeanAttributeInfos(component);
            infos.put(component.getClientId(), info);

            for (UIComponent child : component.getChildren()) {
                collectParameterList(child, parameters, infos);
            }
        }
    }

    public void examineParameters(Map<String, String> parameterMap, FacesContext context) {
        String token = NGSecureUtilities.getSecurityTokenFromRequest();
        List<String> originalToken = NGSecureUtilities.getSecurityToken();
        if ((token == null) || (!(originalToken.contains(token)))) {
            throw new IllegalAccessError(
                    "Security breach - user tried to forge a request twice, or hit the back button twenty times");
        }

        NGSecurityFilter checkedBy = NGSecureUtilities.getCheckedBy();
        if (null != checkedBy) {
            for (String key : parameterMap.keySet()) {
                String value = parameterMap.get(key);
                if (!checkedBy.checkParameter(key, value)) {
                    throw new IllegalAccessError("Security breach - user input violated a filter rule");
                }
            }
        }

    }

    /**
     * {@inheritDoc}
     *
     * @return {@link javax.faces.event.PhaseId#ANY_PHASE}
     */
    @Override
    public PhaseId getPhaseId() {
        return PhaseId.APPLY_REQUEST_VALUES;
    }

    public void lookForUnexpectedParameters(Map<String, String> parameterMap, FacesContext context) {
        Map<String, NGBeanAttributeInfo> infos = new HashMap<String, NGBeanAttributeInfo>();
        List<String> expectedParameters = new ArrayList<String>() {
            {
                add("javax.faces.ViewState");
                add("javax.faces.partial.ajax");
                add("javax.faces.partial.execute");
                add("javax.faces.source");
                add("javax.faces.partial.render");
                add("javax.faces.behavior.event");
                add("javax.faces.partial.event");
            }
        };
        UIComponent root = context.getViewRoot();
        collectParameterList(root, expectedParameters, infos);
        Map<String, String> receivedParams = new HashMap<String, String>();
        receivedParams.putAll(parameterMap);
        for (String p : expectedParameters) {
            if (receivedParams.containsKey(p)) {
                if (!p.startsWith("javax.faces.")) {
                    NGBeanAttributeInfo info = infos.get(p);
                    String value = receivedParams.get(p);
                    if ((null != info) && (info.getMaxSize() > 0)) {
                        if (null != value) {
                            if (value.length() > info.getMaxSize()) {
                                throw new IllegalAccessError(
                                        "Possible security breach - requests contains parameters that are longer than they ought to be.");
                            }
                        }
                    }
                }
                receivedParams.remove(p);
            }
        }
        if (receivedParams.size() > 0) {
            throw new IllegalAccessError("Security breach - requests contains unexpected parameters");
        }
    }
}
