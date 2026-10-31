package review_engine.project.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import review_engine.project.entity.EmailVerificationOtp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OTP persistence with row locking to serialize verification and resend operations.
 */
public interface EmailVerificationOtpRepository extends JpaRepository<EmailVerificationOtp, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EmailVerificationOtp o where o.authIdentity.id = :identityId " +
            "and o.consumedAt is null order by o.createdAt desc")
    List<EmailVerificationOtp> findActiveForUpdate(@Param("identityId") UUID identityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EmailVerificationOtp o where o.authIdentity.id = :identityId " +
            "order by o.createdAt desc")
    List<EmailVerificationOtp> findAllForIdentityForUpdate(@Param("identityId") UUID identityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EmailVerificationOtp o where o.id = :id")
    Optional<EmailVerificationOtp> findByIdForUpdate(@Param("id") UUID id);
}
