package com.capitalone.dashboard.model;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection="changeorder")
public class ChangeOrder extends BaseModel{

    private ObjectId collectorItemId;
    private String timestamp;
    private String changeOrderItem;

    private String changeID;
    private String category;
    private String status;
    private String approvalStatus;
    private String initiatedBy;
    private String assignedTo;
    private String assignmentGroup;
    private String changeCoordinator;
    private String coordinatorPhone;
    private String plannedStart;
    private String plannedEnd;
    private String reason;
    private String phase;
    private String riskAssessment;
    private String priority;
    private String dateEntered;
    private boolean open;
    private String backoutDuration;
    private String closeTime;
    private String extProjectRef;
    private String rFCType2;
    private String company;
    private String title;
    private String subcategory;
    private String sLAAgreementID;
    private String changeModel;
    private boolean validChangeItem;

    public ObjectId getCollectorItemId() { return collectorItemId; }

    public void setCollectorItemId(ObjectId collectorItemId) { this.collectorItemId = collectorItemId; }

    public String getTimestamp() { return timestamp; }

    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getChangeOrderItem() { return changeOrderItem; }

    public void setChangeOrderItem(String changeOrderItem) { this.changeOrderItem = changeOrderItem; }

    public String getChangeID() { return changeID; }

    public void setChangeID(String changeID) { this.changeID = changeID; }

    public String getCategory() { return category; }

    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public String getApprovalStatus() { return approvalStatus; }

    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getInitiatedBy() { return initiatedBy; }

    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public String getAssignedTo() { return assignedTo; }

    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getAssignmentGroup() { return assignmentGroup; }

    public void setAssignmentGroup(String assignmentGroup) { this.assignmentGroup = assignmentGroup; }

    public String getChangeCoordinator() { return changeCoordinator; }

    public void setChangeCoordinator(String changeCoordinator) { this.changeCoordinator = changeCoordinator; }

    public String getCoordinatorPhone() { return coordinatorPhone; }

    public void setCoordinatorPhone(String coordinatorPhone) { this.coordinatorPhone = coordinatorPhone; }

    public String getPlannedStart() { return plannedStart; }

    public void setPlannedStart(String plannedStart) { this.plannedStart = plannedStart; }

    public String getPlannedEnd() { return plannedEnd; }

    public void setPlannedEnd(String plannedEnd) { this.plannedEnd = plannedEnd; }

    public String getReason() { return reason; }

    public void setReason(String reason) { this.reason = reason; }

    public String getPhase() { return phase; }

    public void setPhase(String phase) { this.phase = phase; }

    public String getRiskAssessment() { return riskAssessment; }

    public void setRiskAssessment(String riskAssessment) { this.riskAssessment = riskAssessment; }

    public String getPriority() { return priority; }

    public void setPriority(String priority) { this.priority = priority; }

    public String getDateEntered() { return dateEntered; }

    public void setDateEntered(String dateEntered) { this.dateEntered = dateEntered; }

    public boolean isOpen() { return open; }

    public void setOpen(boolean open) { this.open = open; }

    public void setOpen(String open) { this.open = Boolean.parseBoolean(open); }

    public String getBackoutDuration() { return backoutDuration; }

    public void setBackoutDuration(String backoutDuration) { this.backoutDuration = backoutDuration; }

    public String getCloseTime() { return closeTime; }

    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }

    public String getExtProjectRef() { return extProjectRef; }

    public void setExtProjectRef(String extProjectRef) { this.extProjectRef = extProjectRef; }

    public String getrFCType2() { return rFCType2; }

    public void setrFCType2(String rFCType2) { this.rFCType2 = rFCType2; }

    public String getCompany() { return company; }

    public void setCompany(String company) { this.company = company; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getSubcategory() { return subcategory; }

    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }

    public String getsLAAgreementID() { return sLAAgreementID; }

    public void setsLAAgreementID(String sLAAgreementID) { this.sLAAgreementID = sLAAgreementID; }

    public String getChangeModel() { return changeModel; }

    public void setChangeModel(String changeModel) { this.changeModel = changeModel; }

    public void setValidChangeItem(boolean validChangeItem) { this.validChangeItem = validChangeItem; }

    public void setValidChangeItem(String validChangeItem) { this.validChangeItem = Boolean.parseBoolean(validChangeItem); }

    public boolean isValidChangeItem() { return validChangeItem; }

    @Override
    public boolean equals(Object compareTo){
        boolean doesEqual = true;

        if(compareTo == null || !compareTo.getClass().isAssignableFrom(Incident.class)){
            doesEqual = false;
        }else {
            Incident newIncident = (Incident) compareTo;

            if(!newIncident.toString().equals(toString())){
                doesEqual = false;
            }
        }

        return doesEqual;
    }

    /**
     *  Returns human readable string of the ChangeOrder Object.
     *  * equals(Object object) depends on this method. Changing this method could alter the return of the equals method.
     * @return object to string
     */
    @Override
    public String toString() {

        StringBuffer buf = new StringBuffer(210);
        buf.append("changeID: ")
                .append(changeID);

        return buf.toString();
    }

}
