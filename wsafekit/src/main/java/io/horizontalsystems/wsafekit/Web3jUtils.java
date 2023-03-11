package io.horizontalsystems.wsafekit;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Web3jUtils {

    public static String getEth2safeTransactionInput(BigInteger amount, String address){
        String methodName = "eth2safe";
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        Uint256 tokenValue = new Uint256(amount);
        inputParameters.add(tokenValue);
        inputParameters.add(new Utf8String(address));
        TypeReference<Bool> typeReference = new TypeReference<Bool>() {
        };
        outputParameters.add(typeReference);
        Function function = new Function(methodName, inputParameters, outputParameters);
        return FunctionEncoder.encode(function);
    }

    public static String getMatic2safeTransactionInput(BigInteger amount, String address){
        String methodName = "matic2safe";
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        Uint256 tokenValue = new Uint256(amount);
        inputParameters.add(tokenValue);
        inputParameters.add(new Utf8String(address));
        TypeReference<Bool> typeReference = new TypeReference<Bool>() {
        };
        outputParameters.add(typeReference);
        Function function = new Function(methodName, inputParameters, outputParameters);
        return FunctionEncoder.encode(function);
    }

}
