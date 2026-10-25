package br.com.nevvesdev.alcada.application.support;

import br.com.nevvesdev.alcada.application.exception.LimitConfirmationException;
import br.com.nevvesdev.alcada.application.exception.LimitReleaseException;
import br.com.nevvesdev.alcada.application.exception.LimitReservationException;
import br.com.nevvesdev.alcada.application.port.out.LimitReservationPort;
import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.Money;

import java.util.ArrayList;
import java.util.List;

public class FakeLimitReservation implements LimitReservationPort {

    private final List<CreditRequestId> reserved = new ArrayList<>();
    private final List<CreditRequestId> confirmed = new ArrayList<>();
    private final List<CreditRequestId> released = new ArrayList<>();
    private boolean failReserve;
    private boolean failConfirm;
    private boolean failRelease;

    public void failReserve(boolean value) {
        this.failReserve = value;
    }

    public void failConfirm(boolean value) {
        this.failConfirm = value;
    }

    public void failRelease(boolean value) {
        this.failRelease = value;
    }

    public int reservedCount() {
        return reserved.size();
    }

    public int confirmedCount() {
        return confirmed.size();
    }

    public int releasedCount() {
        return released.size();
    }

    @Override
    public void reserve(CreditRequestId requestId, Cpf cpf, Money amount) {
        if (failReserve) {
            throw new LimitReservationException("Falha ao reservar limite");
        }
        reserved.add(requestId);
    }

    @Override
    public void confirm(CreditRequestId requestId) {
        if (failConfirm) {
            throw new LimitConfirmationException("Falha ao confirmar limite");
        }
        confirmed.add(requestId);
    }

    @Override
    public void release(CreditRequestId requestId) {
        if (failRelease) {
            throw new LimitReleaseException("Falha ao liberar reserva");
        }
        released.add(requestId);
    }
}