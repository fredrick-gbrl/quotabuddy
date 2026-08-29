package com.quotabuddy.app;

import java.time.LocalDate;

public final class Models {
    private Models() {}

    /** Direzione di un debito extra-Family: l'altra persona deve dare dei soldi a me. */
    public static final String DEBT_THEY_OWE = "THEY";
    /** Direzione di un debito extra-Family: sono io a dover dare dei soldi all'altra persona. */
    public static final String DEBT_I_OWE = "ME";

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

    /** Debito o credito generico verso una persona, indipendente dalle Family. */
    public static final class Debt {
        public long id;
        public long personId;
        public long amountCents;
        /** DEBT_THEY_OWE oppure DEBT_I_OWE. */
        public String direction;
        public LocalDate happenedOn;
        public String note;
        public boolean settled;
        public LocalDate settledOn;
        public String personName;
        public boolean theyOwe() { return DEBT_THEY_OWE.equals(direction); }
        /** Importo con segno: positivo se la persona deve a me, negativo se io devo a lei. */
        public long signedCents() { return theyOwe() ? amountCents : -amountCents; }
    }

    /** Saldo aggregato dei debiti aperti con una singola persona. */
    public static final class DebtBalance {
        public Person person;
        public long theyOweCents;
        public long iOweCents;
        public int openCount;
        /** Positivo se la persona è in debito con me, negativo se sono io in debito con lei. */
        public long netCents() { return theyOweCents - iOweCents; }
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
