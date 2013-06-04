/**
 *  Copyright 2013 Society for Health Information Systems Programmes, India (HISP India)
 *
 *  This file is part of Billing module.
 *
 *  Billing module is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.

 *  Billing module is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Billing module.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  author: ghanshyam
 *  date: 3-june-2013
 *  issue no: #1632
 **/

package org.openmrs.module.billing.web.controller.billingqueue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.math.NumberUtils;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.billing.includable.billcalculator.BillCalculatorForBDService;
import org.openmrs.module.hospitalcore.BillingService;
import org.openmrs.module.hospitalcore.PatientDashboardService;
import org.openmrs.module.hospitalcore.model.BillableService;
import org.openmrs.module.hospitalcore.model.OpdOrder;
import org.openmrs.module.hospitalcore.model.PatientServiceBill;
import org.openmrs.module.hospitalcore.model.PatientServiceBillItem;
import org.openmrs.module.hospitalcore.util.HospitalCoreUtils;
import org.openmrs.module.hospitalcore.util.Money;
import org.openmrs.module.hospitalcore.util.PatientUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller("ProcedureInvestigationOrderController")
@RequestMapping("/module/billing/procedureinvestigationorder.form")
public class ProcedureInvestigationOrderController {
	@RequestMapping(method = RequestMethod.GET)
	public String main(Model model, @RequestParam("patientId") Integer patientId,
			@RequestParam("encounterId") Integer encounterId) {
		BillingService billingService = Context.getService(BillingService.class);
		List<BillableService> serviceOrderList = billingService.listOfServiceOrder(patientId,encounterId);
		model.addAttribute("serviceOrderList", serviceOrderList);
		model.addAttribute("serviceOrderSize", serviceOrderList.size());
		model.addAttribute("patientId", patientId);
		model.addAttribute("encounterId", encounterId);
		return "/module/billing/queue/procedureInvestigationOrder";
	}

	@RequestMapping(method = RequestMethod.POST)
	public String onSubmit(Model model, Object command,
			HttpServletRequest request,
			@RequestParam("patientId") Integer patientId,
			@RequestParam("encounterId") Integer encounterId,
			@RequestParam("indCount") Integer indCount,
			@RequestParam(value = "billType", required = false) String billType) {

		BillingService billingService = Context.getService(BillingService.class);
		
		PatientDashboardService patientDashboardService = Context.getService(PatientDashboardService.class);

		PatientService patientService = Context.getPatientService();

		// Get the BillCalculator to calculate the rate of bill item the patient has to pay
		Patient patient = patientService.getPatient(patientId);
		Map<String, String> attributes = PatientUtils.getAttributes(patient);

		BillCalculatorForBDService calculator = new BillCalculatorForBDService();

		PatientServiceBill bill = new PatientServiceBill();
		bill.setCreatedDate(new Date());
		bill.setPatient(patient);
		bill.setCreator(Context.getAuthenticatedUser());

		PatientServiceBillItem item;
		String servicename;
		int quantity = 0;
		String selectservice;
		BigDecimal unitPrice;
		String reschedule;
		String paybill;
		BillableService service;
		Money mUnitPrice;
		Money itemAmount;
		Money totalAmount = new Money(BigDecimal.ZERO);
		BigDecimal rate;
		String billTyp;
		BigDecimal totalActualAmount = new BigDecimal(0);
		OpdOrder opdOrder=new OpdOrder();
	
		for (Integer i = 1; i <= indCount; i++) {
			servicename = request.getParameter(i.toString() + "service");
			quantity = NumberUtils.createInteger(request.getParameter(i.toString()+ "servicequantity"));
			selectservice = request.getParameter(i.toString() + "selectservice");
			reschedule = request.getParameter(i.toString() + "reschedule");
			paybill = request.getParameter(i.toString() + "paybill");
			unitPrice = NumberUtils.createBigDecimal(request.getParameter(i.toString() + "unitprice"));
			//ConceptService conceptService = Context.getConceptService();
			//Concept con = conceptService.getConcept("servicename");
			service = billingService.getServiceByConceptName(servicename);

			mUnitPrice = new Money(unitPrice);
			itemAmount = mUnitPrice.times(quantity);
			totalAmount = totalAmount.plus(itemAmount);

			item = new PatientServiceBillItem();
			item.setCreatedDate(new Date());
			item.setName(servicename);
			item.setPatientServiceBill(bill);
			item.setQuantity(quantity);
			item.setService(service);
			item.setUnitPrice(unitPrice);

			item.setAmount(itemAmount.getAmount());

			// Get the ratio for each bill item
			Map<String, Object> parameters = HospitalCoreUtils.buildParameters(
					"patient", patient, "attributes", attributes, "billItem",
					item, "request", request);
			
			if("pay".equals( paybill)){
				billTyp = "paid";
			}
			else{
				billTyp = "free";
			
			}
			
			rate = calculator.getRate(parameters, billTyp);
			item.setActualAmount(item.getAmount().multiply(rate));
			totalActualAmount = totalActualAmount.add(item.getActualAmount());
			bill.addBillItem(item);
		
			if(selectservice.equals("billed")){
			opdOrder=billingService.getOpdTestOrder(encounterId,service.getConceptId());
			opdOrder.setBillingStatus(1);
			patientDashboardService.saveOrUpdateOpdOrder(opdOrder);
			}
			else{
				opdOrder=billingService.getOpdTestOrder(encounterId,service.getConceptId());
				opdOrder.setCancelStatus(1);
				patientDashboardService.saveOrUpdateOpdOrder(opdOrder);
			}
		}

		bill.setAmount(totalAmount.getAmount());
		bill.setActualAmount(totalActualAmount);
		bill.setFreeBill(2);
		bill.setReceipt(billingService.createReceipt());
		bill = billingService.savePatientServiceBill(bill);
	
		return "redirect:/module/billing/patientServiceBillForBD.list?patientId=" + patientId + "&billId="
        + bill.getPatientServiceBillId() + "&billType=" + billType;
	}
}