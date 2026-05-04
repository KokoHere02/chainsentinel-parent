package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import com.chainsentinel.core.service.dto.TradeAccountCreateCommand;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.repository.TradeAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTradeAccountServiceTest {

	private static final String BASE64_KEY = "E68oWUgtnhEu1d6sLWPdiw==";

	@Mock
	private TradeAccountRepository tradeAccountRepository;

	@Test
	void shouldCreateTradeAccountWithEncryptedSecrets() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		DefaultTradeAccountService service = new DefaultTradeAccountService(
			tradeAccountRepository,
			cryptoUtil,
			List.of()
		);
		TradeAccountCreateCommand command = new TradeAccountCreateCommand(
			"okx-main",
			"okx",
			"API_KEY",
			"SIMULATED",
			"api-key-123456",
			"secret-1",
			"pass-1",
			true,
			"demo"
		);
		when(tradeAccountRepository.save(any(TradeAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.create(command, 1L);

		assertEquals("OKX", result.provider());
		assertEquals("okx-main", result.name());
		assertTrue(result.hasApiSecret());
		assertTrue(result.hasPassphrase());
	}

	@Test
	void shouldTestConnectivityWithRegisteredChecker() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity entity = new TradeAccountEntity();
		entity.setName("okx-main");
		entity.setProvider("OKX");
		entity.setAccountType("API_KEY");
		entity.setEnvType("SIMULATED");
		entity.setApiKey("api-key-123456");
		entity.setApiSecretCipher(cryptoUtil.encrypt("secret-1"));
		entity.setPassphraseCipher(cryptoUtil.encrypt("pass-1"));
		entity.setEnabled(true);

		TradeAccountConnectivityChecker checker = new TradeAccountConnectivityChecker() {
			@Override
			public String provider() {
				return "OKX";
			}

			@Override
			public TradeConnectivityCheckResult test(TradeAccountEntity account, String apiSecret, String passphrase) {
				return new TradeConnectivityCheckResult("secret-1".equals(apiSecret) && "pass-1".equals(passphrase), "ok");
			}
		};

		DefaultTradeAccountService service = new DefaultTradeAccountService(
			tradeAccountRepository,
			cryptoUtil,
			List.of(checker)
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(entity));

		var result = service.testConnectivity(1L);

		assertTrue(result.success());
		assertEquals("ok", result.message());
	}

	@Test
	void shouldReturnUnsupportedWhenCheckerMissing() {
		AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
		TradeAccountEntity entity = new TradeAccountEntity();
		entity.setProvider("BINANCE");
		entity.setAccountType("API_KEY");
		entity.setEnvType("SIMULATED");
		entity.setEnabled(true);

		DefaultTradeAccountService service = new DefaultTradeAccountService(
			tradeAccountRepository,
			cryptoUtil,
			List.of()
		);
		when(tradeAccountRepository.findById(1L)).thenReturn(Optional.of(entity));

		var result = service.testConnectivity(1L);

		assertFalse(result.success());
	}
}
