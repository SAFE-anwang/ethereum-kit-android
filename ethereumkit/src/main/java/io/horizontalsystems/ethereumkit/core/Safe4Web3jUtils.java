package io.horizontalsystems.ethereumkit.core;

import android.util.Log;

import org.web3j.abi.DefaultFunctionReturnDecoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Safe4Web3jUtils {
    public static String getDepositTransactionInput(String address, BigInteger lockDay) {
        String methodName = "deposit";
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        Uint256 lockDayValue = new Uint256(lockDay);
        inputParameters.add(new Utf8String(address));
        inputParameters.add(lockDayValue);
        TypeReference<Uint256> typeReference = new TypeReference<Uint256>() {
        };
        outputParameters.add(typeReference);
        Function function = new Function(methodName, Arrays.asList(new Address(address), new Uint256(lockDay)), outputParameters);
        return FunctionEncoder.encode(function);
    }


    public static String depositDecode(String input) {
        String address = DefaultFunctionReturnDecoder.decodeAddress(input);
        return address;
    }
}
