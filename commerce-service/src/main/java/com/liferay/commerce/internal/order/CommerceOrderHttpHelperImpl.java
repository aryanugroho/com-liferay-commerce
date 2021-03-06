/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.commerce.internal.order;

import com.liferay.commerce.constants.CommerceOrderConstants;
import com.liferay.commerce.constants.CommercePortletKeys;
import com.liferay.commerce.constants.CommerceWebKeys;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.order.CommerceOrderHttpHelper;
import com.liferay.commerce.organization.service.CommerceOrganizationLocalService;
import com.liferay.commerce.organization.util.CommerceOrganizationHelper;
import com.liferay.commerce.service.CommerceOrderItemService;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.commerce.service.CommerceOrderService;
import com.liferay.petra.lang.CentralizedThreadLocal;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Organization;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.PortletURLFactory;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.CookieKeys;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.SessionParamUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Marco Leo
 * @author Andrea Di Giorgi
 */
@Component(immediate = true)
public class CommerceOrderHttpHelperImpl implements CommerceOrderHttpHelper {

	@Override
	public CommerceOrder addCommerceOrder(HttpServletRequest httpServletRequest)
		throws PortalException {

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		CommerceContext commerceContext = _getCommerceContext(
			httpServletRequest);

		CommerceOrder commerceOrder = null;

		long commerceCurrencyId = 0;

		CommerceCurrency commerceCurrency =
			commerceContext.getCommerceCurrency();

		if (commerceCurrency != null) {
			commerceCurrencyId = commerceCurrency.getCommerceCurrencyId();
		}

		Organization organization =
			_commerceOrganizationHelper.getCurrentOrganization(
				httpServletRequest);

		if (organization != null) {
			commerceOrder = _commerceOrderService.addOrganizationCommerceOrder(
				organization.getGroupId(), themeDisplay.getSiteGroupId(),
				organization.getOrganizationId(), commerceCurrencyId, 0, null);
		}
		else {
			commerceOrder = _commerceOrderLocalService.addUserCommerceOrder(
				themeDisplay.getScopeGroupId(), themeDisplay.getUserId(),
				commerceCurrencyId);
		}

		setCurrentCommerceOrder(httpServletRequest, commerceOrder);

		return commerceOrder;
	}

	@Override
	public PortletURL getCommerceCartPortletURL(
			HttpServletRequest httpServletRequest)
		throws PortalException {

		CommerceOrder commerceOrder = getCurrentCommerceOrder(
			httpServletRequest);

		return getCommerceCartPortletURL(httpServletRequest, commerceOrder);
	}

	@Override
	public PortletURL getCommerceCartPortletURL(
			HttpServletRequest httpServletRequest, CommerceOrder commerceOrder)
		throws PortalException {

		long groupId = _portal.getScopeGroupId(httpServletRequest);

		long plid = _portal.getPlidFromPortletId(
			groupId, CommercePortletKeys.COMMERCE_CART_CONTENT);

		if (plid > 0) {
			return _getPortletURL(
				httpServletRequest, CommercePortletKeys.COMMERCE_CART_CONTENT);
		}

		plid = _portal.getPlidFromPortletId(
			groupId, CommercePortletKeys.COMMERCE_ORDER_CONTENT);

		if (plid > 0) {
			PortletURL portletURL = _getPortletURL(
				httpServletRequest, CommercePortletKeys.COMMERCE_ORDER_CONTENT);

			if (commerceOrder != null) {
				portletURL.setParameter(
					"mvcRenderCommandName", "viewCommerceOrderItems");
				portletURL.setParameter(
					"commerceOrderId",
					String.valueOf(commerceOrder.getCommerceOrderId()));
			}

			return portletURL;
		}

		plid = _portal.getPlidFromPortletId(
			groupId, CommercePortletKeys.COMMERCE_ORGANIZATION_ORDER);

		if (plid > 0) {
			PortletURL portletURL = _getPortletURL(
				httpServletRequest,
				CommercePortletKeys.COMMERCE_ORGANIZATION_ORDER);

			if (commerceOrder != null) {
				portletURL.setParameter(
					"mvcRenderCommandName", "editCommerceOrder");
				portletURL.setParameter(
					"commerceOrderId",
					String.valueOf(commerceOrder.getCommerceOrderId()));
			}

			return portletURL;
		}

		return null;
	}

	@Override
	public PortletURL getCommerceCheckoutPortletURL(
			HttpServletRequest httpServletRequest)
		throws PortalException {

		return _getPortletURL(
			httpServletRequest, CommercePortletKeys.COMMERCE_CHECKOUT);
	}

	@Override
	public int getCommerceOrderItemsQuantity(
			HttpServletRequest httpServletRequest)
		throws PortalException {

		CommerceOrder commerceOrder = getCurrentCommerceOrder(
			httpServletRequest);

		if (commerceOrder == null) {
			return 0;
		}

		return _commerceOrderItemService.getCommerceOrderItemsQuantity(
			commerceOrder.getCommerceOrderId());
	}

	@Override
	public CommerceOrder getCurrentCommerceOrder(
			HttpServletRequest httpServletRequest)
		throws PortalException {

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		CommerceOrder commerceOrder = null;

		Organization organization =
			_commerceOrganizationHelper.getCurrentOrganization(
				httpServletRequest);

		if (organization != null) {
			commerceOrder = _getOrganizationCurrentCommerceOrder(
				themeDisplay, organization);
		}
		else {
			commerceOrder = _getUserCurrentCommerceOrder(themeDisplay);
		}

		if (((commerceOrder != null) && !commerceOrder.isOpen()) ||
			((commerceOrder != null) &&
			 !_commerceOrderModelResourcePermission.contains(
				 themeDisplay.getPermissionChecker(), commerceOrder,
				 ActionKeys.UPDATE))) {

			return null;
		}

		return _checkGuestOrder(themeDisplay, commerceOrder);
	}

	@Override
	public void setCurrentCommerceOrder(
			HttpServletRequest httpServletRequest, CommerceOrder commerceOrder)
		throws PortalException {

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		commerceOrder = _commerceOrderLocalService.recalculatePrice(
			commerceOrder.getCommerceOrderId(),
			_getCommerceContext(httpServletRequest));

		if (!themeDisplay.isSignedIn()) {
			_setGuestCommerceOrder(themeDisplay, commerceOrder);

			return;
		}

		httpServletRequest = _portal.getOriginalServletRequest(
			httpServletRequest);

		HttpSession httpSession = httpServletRequest.getSession();

		httpSession.setAttribute(
			_getCookieName(commerceOrder.getGroupId()),
			commerceOrder.getUuid());
	}

	@Reference(
		target = "(model.class.name=com.liferay.commerce.model.CommerceOrder)",
		unbind = "-"
	)
	protected void setModelResourcePermission(
		ModelResourcePermission<CommerceOrder> modelResourcePermission) {

		_commerceOrderModelResourcePermission = modelResourcePermission;
	}

	private CommerceOrder _checkGuestOrder(
			ThemeDisplay themeDisplay, CommerceOrder commerceOrder)
		throws PortalException {

		if (commerceOrder == null) {
			return null;
		}

		User user = themeDisplay.getUser();

		if ((user == null) || user.isDefaultUser()) {
			return commerceOrder;
		}

		long groupId = themeDisplay.getScopeGroupId();

		String domain = CookieKeys.getDomain(themeDisplay.getRequest());

		String commerceOrderUuidWebKey = _getCookieName(groupId);

		if (commerceOrder.isGuestOrder()) {
			CookieKeys.deleteCookies(
				themeDisplay.getRequest(), themeDisplay.getResponse(), domain,
				commerceOrderUuidWebKey);

			return _commerceOrderService.updateUser(
				commerceOrder.getCommerceOrderId(), user.getUserId());
		}

		String commerceOrderUuid = CookieKeys.getCookie(
			themeDisplay.getRequest(), commerceOrderUuidWebKey, false);

		if (Validator.isNull(commerceOrderUuid)) {
			return commerceOrder;
		}

		CommerceOrder cookieCommerceOrder =
			_commerceOrderService.fetchCommerceOrder(
				commerceOrderUuid, groupId);

		if ((cookieCommerceOrder == null) ||
			!cookieCommerceOrder.isGuestOrder()) {

			return commerceOrder;
		}

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			themeDisplay.getRequest());

		_commerceOrderService.mergeGuestCommerceOrder(
			cookieCommerceOrder.getCommerceOrderId(),
			commerceOrder.getCommerceOrderId(),
			_getCommerceContext(themeDisplay.getRequest()), serviceContext);

		CookieKeys.deleteCookies(
			themeDisplay.getRequest(), themeDisplay.getResponse(), domain,
			commerceOrderUuidWebKey);

		return commerceOrder;
	}

	private CommerceContext _getCommerceContext(
		HttpServletRequest httpServletRequest) {

		return (CommerceContext)httpServletRequest.getAttribute(
			CommerceWebKeys.COMMERCE_CONTEXT);
	}

	private String _getCookieName(long groupId) {
		return CommerceOrder.class.getName() + StringPool.POUND + groupId;
	}

	private CommerceOrder _getOrganizationCurrentCommerceOrder(
			ThemeDisplay themeDisplay, Organization organization)
		throws PortalException {

		CommerceOrder commerceOrder = _commerceOrderUuidThreadLocal.get();

		if (commerceOrder != null) {
			return commerceOrder;
		}

		HttpServletRequest httpServletRequest =
			_portal.getOriginalServletRequest(themeDisplay.getRequest());

		long groupId = organization.getGroupId();

		String uuid = SessionParamUtil.getString(
			httpServletRequest, _getCookieName(groupId));

		if (Validator.isNotNull(uuid)) {
			commerceOrder = _commerceOrderService.fetchCommerceOrder(
				uuid, groupId);
		}

		if (commerceOrder == null) {
			commerceOrder = _commerceOrderService.fetchCommerceOrder(
				groupId, CommerceOrderConstants.ORDER_STATUS_OPEN);
		}

		return commerceOrder;
	}

	private PortletURL _getPortletURL(
			HttpServletRequest httpServletRequest, String portletId)
		throws PortalException {

		PortletURL portletURL = null;

		long groupId = _portal.getScopeGroupId(httpServletRequest);

		long plid = _portal.getPlidFromPortletId(groupId, portletId);

		if (plid > 0) {
			portletURL = _portletURLFactory.create(
				httpServletRequest, portletId, plid,
				PortletRequest.RENDER_PHASE);
		}
		else {
			portletURL = _portletURLFactory.create(
				httpServletRequest, portletId, PortletRequest.RENDER_PHASE);
		}

		return portletURL;
	}

	private CommerceOrder _getUserCurrentCommerceOrder(
			ThemeDisplay themeDisplay)
		throws PortalException {

		CommerceOrder commerceOrder = _commerceOrderUuidThreadLocal.get();

		if (commerceOrder != null) {
			return commerceOrder;
		}

		User user = themeDisplay.getUser();

		if ((user != null) && !user.isDefaultUser()) {
			commerceOrder = _commerceOrderService.fetchCommerceOrder(
				themeDisplay.getScopeGroupId(),
				CommerceOrderConstants.ORDER_STATUS_OPEN);

			if (commerceOrder != null) {
				_commerceOrderUuidThreadLocal.set(commerceOrder);

				return commerceOrder;
			}
		}

		String cookieName = _getCookieName(themeDisplay.getScopeGroupId());

		String commerceOrderUuid = CookieKeys.getCookie(
			themeDisplay.getRequest(), cookieName, true);

		if (Validator.isNotNull(commerceOrderUuid)) {
			commerceOrder = _commerceOrderService.fetchCommerceOrder(
				commerceOrderUuid, themeDisplay.getScopeGroupId());

			if (commerceOrder != null) {
				_commerceOrderUuidThreadLocal.set(commerceOrder);

				return commerceOrder;
			}
		}

		return commerceOrder;
	}

	private void _setGuestCommerceOrder(
			ThemeDisplay themeDisplay, CommerceOrder commerceOrder)
		throws PortalException {

		User user = themeDisplay.getUser();

		if ((user != null) && !user.isDefaultUser()) {
			return;
		}

		String commerceOrderUuidWebKey = _getCookieName(
			commerceOrder.getSiteGroupId());

		Cookie cookie = new Cookie(
			commerceOrderUuidWebKey, commerceOrder.getUuid());

		String domain = CookieKeys.getDomain(themeDisplay.getRequest());

		if (Validator.isNotNull(domain)) {
			cookie.setDomain(domain);
		}

		cookie.setMaxAge(CookieKeys.MAX_AGE);
		cookie.setPath(StringPool.SLASH);

		CookieKeys.addCookie(
			themeDisplay.getRequest(), themeDisplay.getResponse(), cookie);
	}

	private static ModelResourcePermission<CommerceOrder>
		_commerceOrderModelResourcePermission;
	private static final ThreadLocal<CommerceOrder>
		_commerceOrderUuidThreadLocal = new CentralizedThreadLocal<>(
			CommerceOrderHttpHelperImpl.class.getName());

	@Reference
	private CommerceOrderItemService _commerceOrderItemService;

	@Reference
	private CommerceOrderLocalService _commerceOrderLocalService;

	@Reference
	private CommerceOrderService _commerceOrderService;

	@Reference
	private CommerceOrganizationHelper _commerceOrganizationHelper;

	@Reference
	private CommerceOrganizationLocalService _commerceOrganizationLocalService;

	@Reference
	private Portal _portal;

	@Reference
	private PortletURLFactory _portletURLFactory;

}