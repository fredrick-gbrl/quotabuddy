package com.quotabuddy.app;

import java.time.LocalDate;

public final class Models {
    private Models() {}

    public static final class Person {
        public long id;
        public String name;
        public String method;
        public String note;
    }

    public static final class Subscription {
        public long id;
        public String name;
        public LocalDate startDate;
        public int renewalDay;
        public boolean includeOwner;
        public boolean archived;
        public String color;
    }

    public static final class Membership {
        public long id;
        public long subscriptionId;
        public long personId;
        public LocalDate joinedOn;
        public LocalDate leftOn;
        public String note;
    }

    public static final class PricePoint {
        public long id;
        public long subscriptionId;
        public long amountCents;
        public LocalDate validFrom;
    }

    public static final class Payment {
        public long id;
        public long subscriptionId;
        public long personId;
        public long amountCents;
        public LocalDate paidAt;
        public String method;
        public String note;
        public String personName;
        public String subscriptionName;
        public String subscriptionColor;
        public boolean pending;
    }

    public enum MemberState { PAID, CREDIT, LATE, UPCOMING, ENDED }

    public static final class MemberSnapshot {
        public Membership membership;
        public Person person;
        public Subscription subscription;
        public MemberState state;
        public long totalPaidCents;
        public long dueThroughTodayCents;
        public long balanceCents;
        public long outstandingCents;
        public long advanceCents;
        public long currentShareCents;
        public LocalDate coveredThrough;
        public LocalDate coveredThroughEnd;
        public LocalDate nextChargeDate;
        public long nextChargeCents;
        public long nextChargeAlreadyCoveredCents;
        public long nextChargeRemainingCents;
    }

    public static final class SubscriptionSummary {
        public Subscription subscription;
        public long currentPriceCents;
        public int activeMembers;
        public long memberShareCents;
        public long expectedFromMembersCents;
        public long outstandingCents;
        public long advanceCents;
        public int lateMembers;
        public LocalDate nextRenewal;
    }
}
